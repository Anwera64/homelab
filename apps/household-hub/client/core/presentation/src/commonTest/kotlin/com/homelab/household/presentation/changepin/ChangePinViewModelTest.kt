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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

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
    fun the_hub_takes_the_change_and_the_screen_says_so() = runTest(testDispatcher) {
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
    fun two_different_new_pins_land_under_the_second_field() = runTest(testDispatcher) {
        val viewModel = ChangePinViewModel(changePin)
        viewModel.onCurrentChange("135790")
        viewModel.onNewChange("864209")
        viewModel.onAgainChange("864200")

        viewModel.change()
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.againMismatch)
    }

    @Test
    fun a_new_pin_that_is_not_six_digits_says_so_under_the_field() = runTest(testDispatcher) {
        val viewModel = ChangePinViewModel(changePin)
        viewModel.onCurrentChange("135790")
        viewModel.onNewChange("864")
        viewModel.onAgainChange("864")

        viewModel.change()
        advanceUntilIdle()

        assertEquals(PinError.NotSixDigits, viewModel.uiState.value.newError)
    }

    @Test
    fun a_wrong_current_pin_lands_under_that_field_with_the_tries_left() = runTest(testDispatcher) {
        everySuspend { changePin(any(), any()) } throws WrongPinException(attemptsLeft = 4)
        val viewModel = ChangePinViewModel(changePin)
        filledIn(viewModel)

        viewModel.change()
        advanceUntilIdle()

        assertEquals(CurrentPinError.Wrong(4), viewModel.uiState.value.currentError)
        assertEquals("", viewModel.uiState.value.current)
    }

    @Test
    fun too_many_wrong_pins_count_down() = runTest(testDispatcher) {
        everySuspend { changePin(any(), any()) } throws PinLockedException(retryAfterSeconds = 30)
        val viewModel = ChangePinViewModel(changePin)
        filledIn(viewModel)

        viewModel.change()
        advanceTimeBy(1)

        assertEquals(ChangePinStatus.Locked(secondsLeft = 30), viewModel.uiState.value.status)
    }

    @Test
    fun an_unreachable_hub_says_nothing_was_changed() = runTest(testDispatcher) {
        everySuspend { changePin(any(), any()) } throws ServerOfflineException()
        val viewModel = ChangePinViewModel(changePin)
        filledIn(viewModel)

        viewModel.change()
        advanceUntilIdle()

        assertEquals(ChangePinStatus.Unreachable, viewModel.uiState.value.status)
    }
}
