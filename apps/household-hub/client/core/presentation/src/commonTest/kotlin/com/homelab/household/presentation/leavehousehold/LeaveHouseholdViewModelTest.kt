package com.homelab.household.presentation.leavehousehold

import app.cash.turbine.test
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.exception.SoleAdminException
import com.homelab.household.domain.exception.WrongPinException
import com.homelab.household.domain.usecase.LeaveHouseholdUseCase
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** Leaving the household: your own PIN, and the one refusal nobody can work around. */
@OptIn(ExperimentalCoroutinesApi::class)
class LeaveHouseholdViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val leaveHousehold = mock<LeaveHouseholdUseCase>()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = LeaveHouseholdViewModel(leaveHousehold)

    @Test
    fun the_right_pin_leaves_the_household() = runTest(testDispatcher) {
        everySuspend { leaveHousehold(any()) } returns Unit
        val viewModel = viewModel()
        viewModel.onPinChange("135790")

        viewModel.events.test {
            viewModel.leave()
            advanceUntilIdle()

            assertEquals(LeaveHouseholdEvent.Left, awaitItem())
        }
    }

    @Test
    fun a_wrong_pin_says_how_many_tries_are_left() = runTest(testDispatcher) {
        everySuspend { leaveHousehold(any()) } throws WrongPinException(attemptsLeft = 4)
        val viewModel = viewModel()
        viewModel.onPinChange("000000")

        viewModel.leave()
        advanceUntilIdle()

        assertEquals(LeaveHouseholdStatus.WrongPin(4), viewModel.uiState.value.status)
    }

    @Test
    fun the_only_admin_is_refused_by_the_hub_and_told_why() = runTest(testDispatcher) {
        everySuspend { leaveHousehold(any()) } throws SoleAdminException()
        val viewModel = viewModel()
        viewModel.onPinChange("135790")

        viewModel.leave()
        advanceUntilIdle()

        assertEquals(LeaveHouseholdStatus.SoleAdmin, viewModel.uiState.value.status)
    }

    @Test
    fun an_unreachable_hub_says_nothing_was_changed() = runTest(testDispatcher) {
        everySuspend { leaveHousehold(any()) } throws ServerOfflineException()
        val viewModel = viewModel()
        viewModel.onPinChange("135790")

        viewModel.leave()
        advanceUntilIdle()

        assertEquals(LeaveHouseholdStatus.Unreachable, viewModel.uiState.value.status)
    }
}
