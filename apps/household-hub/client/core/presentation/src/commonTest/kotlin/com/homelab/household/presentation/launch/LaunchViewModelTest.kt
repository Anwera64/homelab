package com.homelab.household.presentation.launch

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.homelab.household.domain.exception.NotFoundException
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.exception.UnexpectedContentTypeException
import com.homelab.household.domain.exception.UpstreamGatewayException
import com.homelab.household.domain.model.AuthStatus
import com.homelab.household.domain.usecase.CheckAuthStatusUseCase
import com.homelab.household.domain.usecase.GetHubHostUseCase
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * Launch is only reached by a phone with nobody signed in. It asks the hub where it stands and
 * moves on the moment it knows — without stopping to say so. A hub that can't be read, for any
 * reason, keeps the user here and is asked again by itself.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LaunchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val checkAuthStatusUseCase = mock<CheckAuthStatusUseCase>()
    private val getHubHostUseCase = mock<GetHubHostUseCase>()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { getHubHostUseCase() } returns "hub.spicy-llama.duckdns.org"
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = LaunchViewModel(
        checkAuthStatusUseCase = checkAuthStatusUseCase,
        getHubHostUseCase = getHubHostUseCase
    )

    /**
     * A hub that can't be read is asked again every ten seconds, forever — which is right for the
     * app and endless for `runTest`, which runs every pending task before it returns. A test that
     * leaves the hub failing stops the ViewModel itself.
     */
    private fun LaunchViewModel.stop() = viewModelScope.cancel()

    @Test
    fun it_names_the_hub_it_is_reaching_before_any_answer() = runTest(testDispatcher) {
        everySuspend { checkAuthStatusUseCase() } returns AuthStatus(isInitialized = true, memberCount = 1)

        val state = viewModel().uiState.value

        assertEquals("hub.spicy-llama.duckdns.org", state.hubAddress)
        assertEquals(HubStatus.Checking, state.status)
    }

    @Test
    fun a_hub_with_members_leads_to_sign_in_straight_from_checking() = runTest(testDispatcher) {
        everySuspend { checkAuthStatusUseCase() } returns AuthStatus(isInitialized = true, memberCount = 2)
        val viewModel = viewModel()

        viewModel.events.test {
            advanceUntilIdle()

            assertEquals(LaunchEvent.GoToSignIn, awaitItem())
            assertEquals(HubStatus.Checking, viewModel.uiState.value.status)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun an_empty_hub_leads_to_first_run_straight_from_checking() = runTest(testDispatcher) {
        everySuspend { checkAuthStatusUseCase() } returns AuthStatus(isInitialized = false, memberCount = 0)
        val viewModel = viewModel()

        viewModel.events.test {
            advanceUntilIdle()

            assertEquals(LaunchEvent.GoToFirstRun, awaitItem())
            assertEquals(HubStatus.Checking, viewModel.uiState.value.status)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun an_offline_hub_has_no_route_and_counts_down_to_asking_again() = runTest(testDispatcher) {
        everySuspend { checkAuthStatusUseCase() } throws ServerOfflineException()
        val viewModel = viewModel()

        viewModel.events.test {
            runCurrent()
            assertEquals(HubStatus.Unavailable(HubFailure.NoRoute), viewModel.uiState.value.status)
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
        everySuspend { checkAuthStatusUseCase() } throws ServerOfflineException()
        val viewModel = viewModel()
        runCurrent()
        everySuspend { checkAuthStatusUseCase() } returns AuthStatus(isInitialized = true, memberCount = 1)

        viewModel.events.test {
            advanceTimeBy(10_000)
            runCurrent()

            assertEquals(LaunchEvent.GoToSignIn, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        verifySuspend(VerifyMode.exactly(2)) { checkAuthStatusUseCase() }
        assertNull(viewModel.uiState.value.retryInSeconds)
    }

    @Test
    fun trying_again_by_hand_stops_the_countdown() = runTest(testDispatcher) {
        everySuspend { checkAuthStatusUseCase() } throws ServerOfflineException()
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
        everySuspend { checkAuthStatusUseCase() } throws UpstreamGatewayException(statusCode = 500)

        assertUnavailableAndCountingDown(HubFailure.Upstream(statusCode = 500))
    }

    @Test
    fun a_hub_that_is_not_there_reports_a_bad_address() = runTest(testDispatcher) {
        everySuspend { checkAuthStatusUseCase() } throws NotFoundException()

        assertUnavailableAndCountingDown(HubFailure.AddressNotFound)
    }

    @Test
    fun a_hub_that_answers_with_something_other_than_json_reports_what_it_sent() = runTest(testDispatcher) {
        everySuspend { checkAuthStatusUseCase() } throws UnexpectedContentTypeException("text/html")

        assertUnavailableAndCountingDown(HubFailure.NotJson(contentType = "text/html"))
    }

    @Test
    fun anything_else_is_an_unknown_failure() = runTest(testDispatcher) {
        everySuspend { checkAuthStatusUseCase() } throws IllegalStateException("something odd")

        assertUnavailableAndCountingDown(HubFailure.Unknown)
    }

    /** Not only a hub with no route: one that answered badly may be starting up, so it's asked again too. */
    @Test
    fun a_hub_that_answered_badly_is_asked_again_by_itself() = runTest(testDispatcher) {
        everySuspend { checkAuthStatusUseCase() } throws UpstreamGatewayException(statusCode = 503)
        val viewModel = viewModel()
        runCurrent()
        everySuspend { checkAuthStatusUseCase() } returns AuthStatus(isInitialized = false, memberCount = 0)

        viewModel.events.test {
            advanceTimeBy(10_000)
            runCurrent()

            assertEquals(LaunchEvent.GoToFirstRun, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        verifySuspend(VerifyMode.exactly(2)) { checkAuthStatusUseCase() }
    }

    @Test
    fun retrying_asks_the_hub_again() = runTest(testDispatcher) {
        everySuspend { checkAuthStatusUseCase() } throws ServerOfflineException()
        val viewModel = viewModel()
        runCurrent()

        everySuspend { checkAuthStatusUseCase() } returns AuthStatus(isInitialized = true, memberCount = 1)

        viewModel.events.test {
            viewModel.checkHub()
            advanceUntilIdle()

            assertEquals(LaunchEvent.GoToSignIn, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Every failure lands on the same offline screen, counting down — only the reason differs. */
    private suspend fun TestScope.assertUnavailableAndCountingDown(reason: HubFailure) {
        val viewModel = viewModel()

        viewModel.events.test {
            runCurrent()

            assertEquals(HubStatus.Unavailable(reason), viewModel.uiState.value.status)
            assertEquals(10, viewModel.uiState.value.retryInSeconds)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.stop()
    }
}
