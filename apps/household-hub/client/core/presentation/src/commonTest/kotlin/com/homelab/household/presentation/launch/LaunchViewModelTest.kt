package com.homelab.household.presentation.launch

import app.cash.turbine.test
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.AuthStatus
import com.homelab.household.domain.usecase.CheckAuthStatusUseCase
import com.homelab.household.domain.usecase.GetHubHostUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Launch asks the hub where it stands, then says so — and, when the answer is one the user
 * can act on, sends the screen onward.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LaunchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val checkAuthStatusUseCase = mockk<CheckAuthStatusUseCase>()
    private val getHubHostUseCase = mockk<GetHubHostUseCase>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { getHubHostUseCase() } returns "hub.spicy-llama.duckdns.org"
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = LaunchViewModel(
        checkAuthStatusUseCase = checkAuthStatusUseCase,
        getHubHostUseCase = getHubHostUseCase
    )

    @Test
    fun it_names_the_hub_it_is_reaching_before_any_answer() = runTest(testDispatcher) {
        coEvery { checkAuthStatusUseCase() } returns AuthStatus(isInitialized = true, memberCount = 1)

        val state = viewModel().uiState.value

        assertEquals("hub.spicy-llama.duckdns.org", state.hubAddress)
        assertEquals(HubStatus.Checking, state.status)
    }

    @Test
    fun a_hub_with_members_is_ready_and_leads_to_sign_in() = runTest(testDispatcher) {
        coEvery { checkAuthStatusUseCase() } returns AuthStatus(isInitialized = true, memberCount = 2)
        val viewModel = viewModel()

        viewModel.events.test {
            advanceUntilIdle()

            assertEquals(HubStatus.Ready(memberCount = 2), viewModel.uiState.value.status)
            assertEquals(LaunchEvent.GoToSignIn, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun an_empty_hub_leads_to_first_run() = runTest(testDispatcher) {
        coEvery { checkAuthStatusUseCase() } returns AuthStatus(isInitialized = false, memberCount = 0)
        val viewModel = viewModel()

        viewModel.events.test {
            advanceUntilIdle()

            assertEquals(HubStatus.FirstRun, viewModel.uiState.value.status)
            assertEquals(LaunchEvent.GoToFirstRun, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun an_offline_hub_stays_on_launch() = runTest(testDispatcher) {
        coEvery { checkAuthStatusUseCase() } throws ServerOfflineException()
        val viewModel = viewModel()

        viewModel.events.test {
            advanceUntilIdle()

            assertEquals(HubStatus.Unreachable, viewModel.uiState.value.status)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun a_failing_hub_keeps_its_own_words_and_stays_on_launch() = runTest(testDispatcher) {
        coEvery { checkAuthStatusUseCase() } throws IllegalStateException("Unexpected status 500")
        val viewModel = viewModel()

        viewModel.events.test {
            advanceUntilIdle()

            assertEquals(HubStatus.Failed(message = "Unexpected status 500"), viewModel.uiState.value.status)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun retrying_asks_the_hub_again() = runTest(testDispatcher) {
        coEvery { checkAuthStatusUseCase() } throws ServerOfflineException()
        val viewModel = viewModel()
        advanceUntilIdle()

        coEvery { checkAuthStatusUseCase() } returns AuthStatus(isInitialized = true, memberCount = 1)

        viewModel.events.test {
            viewModel.checkHub()
            advanceUntilIdle()

            assertEquals(HubStatus.Ready(memberCount = 1), viewModel.uiState.value.status)
            assertEquals(LaunchEvent.GoToSignIn, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun a_retry_goes_back_to_checking_while_it_waits() = runTest(testDispatcher) {
        coEvery { checkAuthStatusUseCase() } throws ServerOfflineException()
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.checkHub()

        assertEquals(HubStatus.Checking, viewModel.uiState.value.status)
    }
}
