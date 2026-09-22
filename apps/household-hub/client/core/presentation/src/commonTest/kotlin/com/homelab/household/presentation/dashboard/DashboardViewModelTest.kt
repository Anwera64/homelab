package com.homelab.household.presentation.dashboard

import com.homelab.household.domain.model.HouseholdMilestone
import com.homelab.household.domain.model.ServerStatus
import com.homelab.household.domain.model.User
import com.homelab.household.domain.usecase.GetCurrentUserUseCase
import com.homelab.household.domain.usecase.ListHouseholdMilestonesUseCase
import com.homelab.household.domain.usecase.ListSessionsUseCase
import com.homelab.household.domain.usecase.LogoutUseCase
import com.homelab.household.domain.usecase.ObserveServerStatusUseCase
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private val observeServerStatusUseCase = mock<ObserveServerStatusUseCase>()
    private val listRecentSessionsUseCase = mock<ListSessionsUseCase>()
    private val listHouseholdMilestonesUseCase = mock<ListHouseholdMilestonesUseCase>()
    private val getCurrentUserUseCase = mock<GetCurrentUserUseCase>()
    private val logoutUseCase = mock<LogoutUseCase>()

    private lateinit var viewModel: DashboardViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { observeServerStatusUseCase() } returns flowOf(ServerStatus.Online(latencyMs = 25))
        everySuspend { getCurrentUserUseCase() } returns
            User(
                id = "u-1",
                fullName = "Alice Doe",
                isAdmin = true,
                isActive = true,
            )
        everySuspend { listRecentSessionsUseCase() } returns emptyList()
        everySuspend { listHouseholdMilestonesUseCase() } returns
            listOf(
                HouseholdMilestone(
                    id = "m-1",
                    sourceUserId = "u-1",
                    sourceUsername = "alice",
                    reportingAgentName = "Assistant",
                    summary = "Family vacation planned",
                ),
            )

        viewModel =
            DashboardViewModel(
                observeServerStatusUseCase = observeServerStatusUseCase,
                listRecentSessionsUseCase = listRecentSessionsUseCase,
                listHouseholdMilestonesUseCase = listHouseholdMilestonesUseCase,
                getCurrentUserUseCase = getCurrentUserUseCase,
                logoutUseCase = logoutUseCase,
            )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun load_dashboard_populates_state() =
        runTest(testDispatcher) {
            viewModel.loadDashboard()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("Alice Doe", state.currentUser?.fullName)
            assertTrue(state.serverStatus is ServerStatus.Online)
            assertEquals(1, state.householdMilestones.size)
            assertEquals("Family vacation planned", state.householdMilestones[0].summary)
        }

    @Test
    fun logout_invokes_usecase_and_clears_user() =
        runTest(testDispatcher) {
            everySuspend { logoutUseCase() } returns Unit

            viewModel.logout()
            advanceUntilIdle()

            verifySuspend { logoutUseCase() }
            assertNull(viewModel.uiState.value.currentUser)
        }
}
