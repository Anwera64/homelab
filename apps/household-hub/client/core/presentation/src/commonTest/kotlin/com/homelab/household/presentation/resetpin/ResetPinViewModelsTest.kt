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
    fun the_code_is_upper_cased_and_carried_to_the_new_pin() =
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
    fun a_short_code_is_answered_with_words_rather_than_a_dead_button() =
        runTest(testDispatcher) {
            val viewModel = ResetCodeViewModel()
            viewModel.onCodeChange("p4x")

            viewModel.onContinue()
            advanceUntilIdle()

            assertEquals(true, viewModel.uiState.value.incomplete)
        }

    @Test
    fun the_new_pin_has_to_be_typed_the_same_way_twice() =
        runTest(testDispatcher) {
            val viewModel = NewPinViewModel("P4XN7T", redeemPinReset)
            viewModel.onPinChange("864209")
            viewModel.onAgainChange("864200")

            viewModel.setPin()
            advanceUntilIdle()

            assertEquals(true, viewModel.uiState.value.againMismatch)
        }

    @Test
    fun a_pin_that_is_not_six_digits_says_so_under_the_field() =
        runTest(testDispatcher) {
            val viewModel = NewPinViewModel("P4XN7T", redeemPinReset)
            viewModel.onPinChange("864")
            viewModel.onAgainChange("864")

            viewModel.setPin()
            advanceUntilIdle()

            assertEquals(PinError.NotSixDigits, viewModel.uiState.value.pinError)
        }

    @Test
    fun the_hub_takes_the_new_pin_and_signs_them_in() =
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
    fun a_code_that_has_gone_says_so() =
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
    fun too_much_guessing_counts_down() =
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
    fun an_unreachable_hub_says_so() =
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
