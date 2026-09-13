package com.homelab.household.presentation.launch

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.homelab.household.domain.exception.NotFoundException
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.exception.UnexpectedContentTypeException
import com.homelab.household.domain.exception.UpstreamGatewayException
import com.homelab.household.domain.model.AuthStatus
import com.homelab.household.domain.model.User
import com.homelab.household.domain.usecase.CheckAuthStatusUseCase
import com.homelab.household.domain.usecase.GetCurrentUserUseCase
import com.homelab.household.domain.usecase.GetHubHostUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Launch asks the hub where it stands, then says so — and, when the answer is one the user
 * can act on, sends the screen onward. A hub that doesn't answer is asked again by itself.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LaunchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val checkAuthStatusUseCase = mockk<CheckAuthStatusUseCase>()
    private val getHubHostUseCase = mockk<GetHubHostUseCase>()
    private val getCurrentUserUseCase = mockk<GetCurrentUserUseCase>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { getHubHostUseCase() } returns "hub.spicy-llama.duckdns.org"
        coEvery { getCurrentUserUseCase() } returns null
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = LaunchViewModel(
        checkAuthStatusUseCase = checkAuthStatusUseCase,
        getHubHostUseCase = getHubHostUseCase,
        getCurrentUserUseCase = getCurrentUserUseCase
    )

    /**
     * A hub that stays offline is asked again every ten seconds, forever — which is right for the
     * app and endless for `runTest`, which runs every pending task before it returns. A test that
     * leaves the hub offline stops the ViewModel itself.
     */
    private fun LaunchViewModel.stop() = viewModelScope.cancel()

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
    fun a_member_still_signed_in_on_this_phone_goes_straight_home() = runTest(testDispatcher) {
        coEvery { checkAuthStatusUseCase() } returns AuthStatus(isInitialized = true, memberCount = 2)
        coEvery { getCurrentUserUseCase() } returns User(id = "emma", fullName = "Emma", isAdmin = true, isActive = true)
        val viewModel = viewModel()

        viewModel.events.test {
            advanceUntilIdle()

            assertEquals(LaunchEvent.GoToHome, awaitItem())
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
    fun an_offline_hub_stays_on_launch_and_counts_down_to_asking_again() = runTest(testDispatcher) {
        coEvery { checkAuthStatusUseCase() } throws ServerOfflineException()
        val viewModel = viewModel()

        viewModel.events.test {
            runCurrent()
            assertEquals(HubStatus.Unreachable, viewModel.uiState.value.status)
            assertEquals(10, viewModel.uiState.value.retryInSeconds)

            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(9, viewModel.uiState.value.retryInSeconds)

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.stop()
    }

    @Test
    fun when_the_countdown_runs_out_it_asks_the_hub_again() = runTest(testDispatcher) {
        coEvery { checkAuthStatusUseCase() } throws ServerOfflineException()
        val viewModel = viewModel()
        runCurrent()
        coEvery { checkAuthStatusUseCase() } returns AuthStatus(isInitialized = true, memberCount = 1)

        viewModel.events.test {
            advanceTimeBy(10_000)
            runCurrent()

            assertEquals(LaunchEvent.GoToSignIn, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 2) { checkAuthStatusUseCase() }
        assertNull(viewModel.uiState.value.retryInSeconds)
    }

    @Test
    fun trying_again_by_hand_stops_the_countdown() = runTest(testDispatcher) {
        coEvery { checkAuthStatusUseCase() } throws ServerOfflineException()
        val viewModel = viewModel()
        runCurrent()

        viewModel.checkHub()

        assertEquals(HubStatus.Checking, viewModel.uiState.value.status)
        assertNull(viewModel.uiState.value.retryInSeconds)
        viewModel.stop()
    }

    /**
     * The screen has to say what went wrong in the user's language, so the reason is a type the
     * UI can map — never a sentence the hub or the network happened to produce.
     */
    @Test
    fun a_hub_that_answers_with_an_error_reports_the_status_it_sent() = runTest(testDispatcher) {
        coEvery { checkAuthStatusUseCase() } throws UpstreamGatewayException(statusCode = 500)
        val viewModel = viewModel()

        viewModel.events.test {
            advanceUntilIdle()

            assertEquals(
                HubStatus.Failed(HubFailure.Upstream(statusCode = 500)),
                viewModel.uiState.value.status
            )
            assertNull(viewModel.uiState.value.retryInSeconds)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun a_hub_that_is_not_there_reports_a_bad_address() = runTest(testDispatcher) {
        coEvery { checkAuthStatusUseCase() } throws NotFoundException()
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(HubStatus.Failed(HubFailure.AddressNotFound), viewModel.uiState.value.status)
    }

    @Test
    fun a_hub_that_answers_with_something_other_than_json_reports_what_it_sent() = runTest(testDispatcher) {
        coEvery { checkAuthStatusUseCase() } throws UnexpectedContentTypeException("text/html")
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(
            HubStatus.Failed(HubFailure.NotJson(contentType = "text/html")),
            viewModel.uiState.value.status
        )
    }

    @Test
    fun anything_else_is_an_unknown_failure() = runTest(testDispatcher) {
        coEvery { checkAuthStatusUseCase() } throws IllegalStateException("something odd")
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(HubStatus.Failed(HubFailure.Unknown), viewModel.uiState.value.status)
    }

    @Test
    fun retrying_asks_the_hub_again() = runTest(testDispatcher) {
        coEvery { checkAuthStatusUseCase() } throws ServerOfflineException()
        val viewModel = viewModel()
        runCurrent()

        coEvery { checkAuthStatusUseCase() } returns AuthStatus(isInitialized = true, memberCount = 1)

        viewModel.events.test {
            viewModel.checkHub()
            advanceUntilIdle()

            assertEquals(HubStatus.Ready(memberCount = 1), viewModel.uiState.value.status)
            assertEquals(LaunchEvent.GoToSignIn, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
