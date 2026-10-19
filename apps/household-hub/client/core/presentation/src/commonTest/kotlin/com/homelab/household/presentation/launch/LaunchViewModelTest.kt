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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

    private fun viewModel() =
        LaunchViewModel(
            checkAuthStatusUseCase = checkAuthStatusUseCase,
            getHubHostUseCase = getHubHostUseCase,
        )

    /**
     * A hub that can't be read is asked again every ten seconds, forever — which is right for the
     * app and endless for `runTest`, which runs every pending task before it returns. A test that
     * leaves the hub failing stops the ViewModel itself.
     */
    private fun LaunchViewModel.stop() = viewModelScope.cancel()

    @Test
    fun `GIVEN a hub that has not answered yet WHEN the screen opens THEN it names the hub address and is checking`() =
        runTest(testDispatcher) {
            everySuspend { checkAuthStatusUseCase() } returns AuthStatus(isInitialized = true, memberCount = 1)

            val state = viewModel().uiState.value

            assertEquals("hub.spicy-llama.duckdns.org", state.hubAddress)
            assertEquals(HubStatus.Checking, state.status)
        }

    @Test
    fun `GIVEN a hub with members WHEN the check finishes THEN the screen goes to sign in while the status still reads checking`() =
        runTest(testDispatcher) {
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
    fun `GIVEN an empty hub WHEN the check finishes THEN the screen goes to first run while the status still reads checking`() =
        runTest(testDispatcher) {
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
    fun `GIVEN an offline hub WHEN the check fails THEN it has no route and counts down to asking again`() =
        runTest(testDispatcher) {
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
    fun `GIVEN an offline hub that has since come back WHEN the countdown runs out THEN the hub is asked again and the screen goes to sign in`() =
        runTest(testDispatcher) {
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
    fun `GIVEN an offline hub counting down WHEN trying again by hand THEN the status is checking and the countdown stops`() =
        runTest(testDispatcher) {
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
    fun `GIVEN a hub that answers with an error status WHEN the check fails THEN the failure carries that status and the screen counts down`() =
        runTest(testDispatcher) {
            everySuspend { checkAuthStatusUseCase() } throws UpstreamGatewayException(statusCode = 500)

            assertUnavailableAndCountingDown(HubFailure.Upstream(statusCode = 500))
        }

    @Test
    fun `GIVEN a hub address that is not found WHEN the check fails THEN the failure is a bad address and the screen counts down`() =
        runTest(testDispatcher) {
            everySuspend { checkAuthStatusUseCase() } throws NotFoundException()

            assertUnavailableAndCountingDown(HubFailure.AddressNotFound)
        }

    @Test
    fun `GIVEN a hub that answers with something other than json WHEN the check fails THEN the failure carries what it sent and the screen counts down`() =
        runTest(testDispatcher) {
            everySuspend { checkAuthStatusUseCase() } throws UnexpectedContentTypeException("text/html")

            assertUnavailableAndCountingDown(HubFailure.NotJson(contentType = "text/html"))
        }

    @Test
    fun `GIVEN the check fails in an unexpected way WHEN the check finishes THEN the failure is unknown and the screen counts down`() =
        runTest(testDispatcher) {
            everySuspend { checkAuthStatusUseCase() } throws IllegalStateException("something odd")

            assertUnavailableAndCountingDown(HubFailure.Unknown)
        }

    /** Not only a hub with no route: one that answered badly may be starting up, so it's asked again too. */
    @Test
    fun `GIVEN a hub that answered badly and has since come back WHEN the countdown runs out THEN the hub is asked again and the screen goes to first run`() =
        runTest(testDispatcher) {
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
    fun `GIVEN an offline hub that has since come back WHEN retrying THEN the screen goes to sign in`() =
        runTest(testDispatcher) {
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
