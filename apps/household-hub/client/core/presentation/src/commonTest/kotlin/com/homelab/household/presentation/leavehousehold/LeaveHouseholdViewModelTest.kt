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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun `GIVEN the right pin WHEN leaving THEN the household is left`() =
        runTest(testDispatcher) {
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
    fun `GIVEN a wrong pin WHEN leaving THEN the status says how many tries are left`() =
        runTest(testDispatcher) {
            everySuspend { leaveHousehold(any()) } throws WrongPinException(attemptsLeft = 4)
            val viewModel = viewModel()
            viewModel.onPinChange("000000")

            viewModel.leave()
            advanceUntilIdle()

            assertEquals(LeaveHouseholdStatus.WrongPin(4), viewModel.uiState.value.status)
        }

    @Test
    fun `GIVEN the only admin WHEN leaving THEN the hub refuses and the status says why`() =
        runTest(testDispatcher) {
            everySuspend { leaveHousehold(any()) } throws SoleAdminException()
            val viewModel = viewModel()
            viewModel.onPinChange("135790")

            viewModel.leave()
            advanceUntilIdle()

            assertEquals(LeaveHouseholdStatus.SoleAdmin, viewModel.uiState.value.status)
        }

    @Test
    fun `GIVEN an unreachable hub WHEN leaving THEN the status says nothing was changed`() =
        runTest(testDispatcher) {
            everySuspend { leaveHousehold(any()) } throws ServerOfflineException()
            val viewModel = viewModel()
            viewModel.onPinChange("135790")

            viewModel.leave()
            advanceUntilIdle()

            assertEquals(LeaveHouseholdStatus.Unreachable, viewModel.uiState.value.status)
        }
}
