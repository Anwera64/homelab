package com.homelab.household.presentation.changepin

import app.cash.turbine.test
import com.homelab.household.domain.exception.PinLockedException
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.exception.WrongPinException
import com.homelab.household.domain.usecase.ChangePinUseCase
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

/** Changing your PIN: confirmed with the old one, typed twice, and every other device signs out. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChangePinViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val changePin = mock<ChangePinUseCase>()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun filledIn(viewModel: ChangePinViewModel) {
        viewModel.onCurrentChange("135790")
        viewModel.onNewChange("864209")
        viewModel.onAgainChange("864209")
    }

    @Test
    fun `GIVEN a filled-in form the hub accepts WHEN changing the pin THEN the screen says it changed`() =
        runTest(testDispatcher) {
            everySuspend { changePin(any(), any()) } returns Unit
            val viewModel = ChangePinViewModel(changePin)
            filledIn(viewModel)

            viewModel.events.test {
                viewModel.change()
                advanceUntilIdle()

                assertEquals(ChangePinEvent.Changed, awaitItem())
            }
            verifySuspend(VerifyMode.exactly(1)) { changePin("135790", "864209") }
        }

    @Test
    fun `GIVEN two different new pins WHEN changing the pin THEN the mismatch lands under the second field`() =
        runTest(testDispatcher) {
            val viewModel = ChangePinViewModel(changePin)
            viewModel.onCurrentChange("135790")
            viewModel.onNewChange("864209")
            viewModel.onAgainChange("864200")

            viewModel.change()
            advanceUntilIdle()

            assertEquals(true, viewModel.uiState.value.againMismatch)
        }

    @Test
    fun `GIVEN a new pin that is not six digits WHEN changing the pin THEN the new pin field says so`() =
        runTest(testDispatcher) {
            val viewModel = ChangePinViewModel(changePin)
            viewModel.onCurrentChange("135790")
            viewModel.onNewChange("864")
            viewModel.onAgainChange("864")

            viewModel.change()
            advanceUntilIdle()

            assertEquals(PinError.NotSixDigits, viewModel.uiState.value.newError)
        }

    @Test
    fun `GIVEN a wrong current pin WHEN changing the pin THEN the error lands under that field with the tries left and the field is cleared`() =
        runTest(testDispatcher) {
            everySuspend { changePin(any(), any()) } throws WrongPinException(attemptsLeft = 4)
            val viewModel = ChangePinViewModel(changePin)
            filledIn(viewModel)

            viewModel.change()
            advanceUntilIdle()

            assertEquals(CurrentPinError.Wrong(4), viewModel.uiState.value.currentError)
            assertEquals("", viewModel.uiState.value.current)
        }

    @Test
    fun `GIVEN too many wrong pins WHEN changing the pin THEN the status counts down the lock`() =
        runTest(testDispatcher) {
            everySuspend { changePin(any(), any()) } throws PinLockedException(retryAfterSeconds = 30)
            val viewModel = ChangePinViewModel(changePin)
            filledIn(viewModel)

            viewModel.change()
            advanceTimeBy(1)

            assertEquals(ChangePinStatus.Locked(secondsLeft = 30), viewModel.uiState.value.status)
        }

    @Test
    fun `GIVEN an unreachable hub WHEN changing the pin THEN the status says nothing was changed`() =
        runTest(testDispatcher) {
            everySuspend { changePin(any(), any()) } throws ServerOfflineException()
            val viewModel = ChangePinViewModel(changePin)
            filledIn(viewModel)

            viewModel.change()
            advanceUntilIdle()

            assertEquals(ChangePinStatus.Unreachable, viewModel.uiState.value.status)
        }
}
