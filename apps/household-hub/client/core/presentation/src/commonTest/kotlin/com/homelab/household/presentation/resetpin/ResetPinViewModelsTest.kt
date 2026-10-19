package com.homelab.household.presentation.resetpin

import app.cash.turbine.test
import com.homelab.household.domain.exception.CodeGuessesLockedException
import com.homelab.household.domain.exception.InviteInvalidException
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.User
import com.homelab.household.domain.usecase.RedeemPinResetUseCase
import com.homelab.household.presentation.firstrun.PinError
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Redeeming a reset code: the code on one screen, the PIN it sets on the next. */
@OptIn(ExperimentalCoroutinesApi::class)
class ResetPinViewModelsTest {
    private val testDispatcher = StandardTestDispatcher()
    private val redeemPinReset = mock<RedeemPinResetUseCase>()
    private val emma = User(id = "emma", fullName = "Emma", isAdmin = true, isActive = true)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `GIVEN a lower-case reset code WHEN continuing THEN the screen goes to the new pin with the code upper-cased`() =
        runTest(testDispatcher) {
            val viewModel = ResetCodeViewModel()
            viewModel.onCodeChange("p4xn7t")

            viewModel.events.test {
                viewModel.onContinue()
                advanceUntilIdle()

                assertEquals(ResetCodeEvent.GoToNewPin("P4XN7T"), awaitItem())
            }
        }

    @Test
    fun `GIVEN a reset code that is too short WHEN continuing THEN the screen says the code is incomplete`() =
        runTest(testDispatcher) {
            val viewModel = ResetCodeViewModel()
            viewModel.onCodeChange("p4x")

            viewModel.onContinue()
            advanceUntilIdle()

            assertEquals(true, viewModel.uiState.value.incomplete)
        }

    @Test
    fun `GIVEN two different new pins WHEN setting the pin THEN the mismatch shows under the second field`() =
        runTest(testDispatcher) {
            val viewModel = NewPinViewModel("P4XN7T", redeemPinReset)
            viewModel.onPinChange("864209")
            viewModel.onAgainChange("864200")

            viewModel.setPin()
            advanceUntilIdle()

            assertEquals(true, viewModel.uiState.value.againMismatch)
        }

    @Test
    fun `GIVEN a new pin that is not six digits WHEN setting the pin THEN the pin field says so`() =
        runTest(testDispatcher) {
            val viewModel = NewPinViewModel("P4XN7T", redeemPinReset)
            viewModel.onPinChange("864")
            viewModel.onAgainChange("864")

            viewModel.setPin()
            advanceUntilIdle()

            assertEquals(PinError.NotSixDigits, viewModel.uiState.value.pinError)
        }

    @Test
    fun `GIVEN two matching new pins the hub accepts WHEN setting the pin THEN the member is signed in`() =
        runTest(testDispatcher) {
            everySuspend { redeemPinReset(any(), any()) } returns emma
            val viewModel = NewPinViewModel("P4XN7T", redeemPinReset)
            viewModel.onPinChange("864209")
            viewModel.onAgainChange("864209")

            viewModel.events.test {
                viewModel.setPin()
                advanceUntilIdle()

                assertEquals(NewPinEvent.SignedIn, awaitItem())
            }
            verifySuspend(VerifyMode.exactly(1)) { redeemPinReset("P4XN7T", "864209") }
        }

    @Test
    fun `GIVEN a reset code that has gone WHEN setting the pin THEN the status says invalid`() =
        runTest(testDispatcher) {
            everySuspend { redeemPinReset(any(), any()) } throws InviteInvalidException()
            val viewModel = NewPinViewModel("P4XN7T", redeemPinReset)
            viewModel.onPinChange("864209")
            viewModel.onAgainChange("864209")

            viewModel.setPin()
            advanceUntilIdle()

            assertEquals(NewPinStatus.Invalid, viewModel.uiState.value.status)
        }

    @Test
    fun `GIVEN too much guessing WHEN setting the pin THEN the status counts down the lock`() =
        runTest(testDispatcher) {
            everySuspend { redeemPinReset(any(), any()) } throws CodeGuessesLockedException(retryAfterSeconds = 30)
            val viewModel = NewPinViewModel("P4XN7T", redeemPinReset)
            viewModel.onPinChange("864209")
            viewModel.onAgainChange("864209")

            viewModel.setPin()
            advanceTimeBy(1)

            assertEquals(NewPinStatus.Locked(secondsLeft = 30), viewModel.uiState.value.status)
        }

    @Test
    fun `GIVEN an unreachable hub WHEN setting the pin THEN the status says unreachable`() =
        runTest(testDispatcher) {
            everySuspend { redeemPinReset(any(), any()) } throws ServerOfflineException()
            val viewModel = NewPinViewModel("P4XN7T", redeemPinReset)
            viewModel.onPinChange("864209")
            viewModel.onAgainChange("864209")

            viewModel.setPin()
            advanceUntilIdle()

            assertEquals(NewPinStatus.Unreachable, viewModel.uiState.value.status)
        }
}
