package com.homelab.household.presentation.pinapprove

import com.homelab.household.domain.exception.PinLockedException
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.exception.WrongPinException
import com.homelab.household.domain.model.Member
import com.homelab.household.domain.model.ResetCode
import com.homelab.household.domain.usecase.ApprovePinResetUseCase
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
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

/** One member vouches for another with their own PIN, then reads out the code. */
@OptIn(ExperimentalCoroutinesApi::class)
class PinApproveViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val approvePinReset = mock<ApprovePinResetUseCase>()
    private val emma = Member(id = "emma", name = "Emma", avatarColor = "#3C6E4E")

    @BeforeTest
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = PinApproveViewModel(emma, approvePinReset)

    @Test
    fun their_own_pin_produces_a_code_to_read_out() =
        runTest(testDispatcher) {
            everySuspend { approvePinReset(any(), any()) } returns ResetCode(code = "P4XN7T", expiresInSeconds = 900)
            val viewModel = viewModel()
            viewModel.onPinChange("246801")

            viewModel.approve()
            advanceTimeBy(1)

            assertEquals(PinApproveStatus.Approved("P4XN7T"), viewModel.uiState.value.status)
            assertEquals(900, viewModel.uiState.value.secondsLeft)
        }

    @Test
    fun a_wrong_pin_says_how_many_tries_are_left_and_clears_the_field() =
        runTest(testDispatcher) {
            everySuspend { approvePinReset(any(), any()) } throws WrongPinException(attemptsLeft = 3)
            val viewModel = viewModel()
            viewModel.onPinChange("000000")

            viewModel.approve()
            advanceUntilIdle()

            assertEquals(PinApproveStatus.WrongPin(3), viewModel.uiState.value.status)
            assertEquals("", viewModel.uiState.value.pin)
        }

    @Test
    fun too_many_wrong_pins_count_down_and_ignore_the_pad() =
        runTest(testDispatcher) {
            everySuspend { approvePinReset(any(), any()) } throws PinLockedException(retryAfterSeconds = 30)
            val viewModel = viewModel()
            viewModel.onPinChange("000000")

            viewModel.approve()
            advanceTimeBy(1)
            assertEquals(PinApproveStatus.Locked(secondsLeft = 30), viewModel.uiState.value.status)

            viewModel.onPinChange("246801")
            assertEquals("", viewModel.uiState.value.pin)
        }

    @Test
    fun an_unreachable_hub_says_so() =
        runTest(testDispatcher) {
            everySuspend { approvePinReset(any(), any()) } throws ServerOfflineException()
            val viewModel = viewModel()
            viewModel.onPinChange("246801")

            viewModel.approve()
            advanceUntilIdle()

            assertEquals(PinApproveStatus.Unreachable, viewModel.uiState.value.status)
        }
}
