package com.homelab.household.presentation.dashboard

import com.homelab.household.domain.model.HouseholdMilestone
import com.homelab.household.domain.model.ServerStatus
import com.homelab.household.domain.model.User
import com.homelab.household.domain.usecase.GetCurrentUserUseCase
import com.homelab.household.domain.usecase.ListHouseholdMilestonesUseCase
import com.homelab.household.domain.usecase.ListSessionsUseCase
import com.homelab.household.domain.usecase.LogoutUseCase
import com.homelab.household.domain.usecase.ObserveServerStatusUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val observeServerStatusUseCase = mockk<ObserveServerStatusUseCase>()
    private val listRecentSessionsUseCase = mockk<ListSessionsUseCase>()
    private val listHouseholdMilestonesUseCase = mockk<ListHouseholdMilestonesUseCase>()
    private val getCurrentUserUseCase = mockk<GetCurrentUserUseCase>()
    private val logoutUseCase = mockk<LogoutUseCase>()

    private lateinit var viewModel: DashboardViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { observeServerStatusUseCase() } returns flowOf(ServerStatus.Online(latencyMs = 25))
        coEvery { getCurrentUserUseCase() } returns User(
            id = "u-1",
            fullName = "Alice Doe",
            isAdmin = true,
            isActive = true
        )
        coEvery { listRecentSessionsUseCase() } returns emptyList()
        coEvery { listHouseholdMilestonesUseCase() } returns listOf(
            HouseholdMilestone(
                id = "m-1",
                sourceUserId = "u-1",
                sourceUsername = "alice",
                reportingAgentName = "Assistant",
                summary = "Family vacation planned"
            )
        )

        viewModel = DashboardViewModel(
            observeServerStatusUseCase = observeServerStatusUseCase,
            listRecentSessionsUseCase = listRecentSessionsUseCase,
            listHouseholdMilestonesUseCase = listHouseholdMilestonesUseCase,
            getCurrentUserUseCase = getCurrentUserUseCase,
            logoutUseCase = logoutUseCase
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun load_dashboard_populates_state() = runTest(testDispatcher) {
        viewModel.loadDashboard()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Alice Doe", state.currentUser?.fullName)
        assertTrue(state.serverStatus is ServerStatus.Online)
        assertEquals(1, state.householdMilestones.size)
        assertEquals("Family vacation planned", state.householdMilestones[0].summary)
    }

    @Test
    fun logout_invokes_usecase_and_clears_user() = runTest(testDispatcher) {
        coEvery { logoutUseCase() } returns Unit

        viewModel.logout()
        advanceUntilIdle()

        coVerify { logoutUseCase() }
        assertNull(viewModel.uiState.value.currentUser)
    }
}
