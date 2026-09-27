package com.homelab.household.presentation.googlesignin

import app.cash.turbine.test
import com.homelab.household.domain.exception.GoogleSignInUnavailableException
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.exception.UpstreamGatewayException
import com.homelab.household.domain.model.CalendarSignInFailure
import com.homelab.household.domain.usecase.StartGoogleCalendarSignInUseCase
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Signing in with Google: the hub gives the page, the browser does the rest, and the way back says how it went. */
@OptIn(ExperimentalCoroutinesApi::class)
class GoogleCalendarSignInViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val startSignIn = mock<StartGoogleCalendarSignInUseCase>()
    private val googlePage = "https://accounts.google.com/o/oauth2/v2/auth?state=s"

    @BeforeTest
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = GoogleCalendarSignInViewModel(startSignIn)

    @Test
    fun `GIVEN the hub gives Google's page WHEN the member signs in THEN the browser opens on it`() =
        runTest(testDispatcher) {
            // GIVEN
            everySuspend { startSignIn() } returns googlePage
            val viewModel = viewModel()

            viewModel.events.test {
                // WHEN
                viewModel.signIn()
                advanceUntilIdle()

                // THEN
                assertEquals(GoogleSignInEvent.OpenBrowser(googlePage), awaitItem())
            }
            assertEquals(GoogleSignInStatus.InBrowser, viewModel.uiState.value.status)
        }

    @Test
    fun `GIVEN the hub is still answering WHEN the member signs in THEN the screen shows it is starting`() =
        runTest(testDispatcher) {
            // GIVEN
            val page = CompletableDeferred<String>()
            everySuspend { startSignIn() } calls { page.await() }
            val viewModel = viewModel()

            // WHEN
            viewModel.signIn()
            runCurrent()

            // THEN
            assertEquals(GoogleSignInStatus.Starting, viewModel.uiState.value.status)
            page.complete(googlePage)
        }

    @Test
    fun `GIVEN a sign-in already under way WHEN the button is tapped again THEN the hub is asked once`() =
        runTest(testDispatcher) {
            // GIVEN
            everySuspend { startSignIn() } returns googlePage
            val viewModel = viewModel()
            viewModel.signIn()

            // WHEN
            viewModel.signIn()
            advanceUntilIdle()

            // THEN
            verifySuspend(VerifyMode.exactly(1)) { startSignIn() }
        }

    @Test
    fun `GIVEN the browser comes back connected WHEN it is read THEN the screen hands back to the profile`() =
        runTest(testDispatcher) {
            // GIVEN
            val viewModel = inBrowser()

            viewModel.events.test {
                skipItems(1) // opening the browser, from inBrowser()

                // WHEN
                viewModel.onBrowserReturned("hyggehub://calendar/connected")
                advanceUntilIdle()

                // THEN
                assertEquals(GoogleSignInEvent.Connected, awaitItem())
            }
        }

    @Test
    fun `GIVEN the member did not share their calendar WHEN the browser comes back THEN the screen says so`() =
        runTest(testDispatcher) {
            // GIVEN
            val viewModel = inBrowser()

            // WHEN
            viewModel.onBrowserReturned("hyggehub://calendar/failed?reason=denied")

            // THEN
            assertEquals(GoogleSignInStatus.Failed(CalendarSignInFailure.DENIED), viewModel.uiState.value.status)
        }

    @Test
    fun `GIVEN the browser was closed early WHEN it comes back empty THEN the screen is ready again with no error`() =
        runTest(testDispatcher) {
            // GIVEN
            val viewModel = inBrowser()

            // WHEN
            viewModel.onBrowserReturned(null)

            // THEN
            assertEquals(GoogleSignInStatus.Idle, viewModel.uiState.value.status)
        }

    @Test
    fun `GIVEN the hub can't be reached WHEN the member signs in THEN the screen says the hub is out of reach`() =
        runTest(testDispatcher) {
            // GIVEN
            everySuspend { startSignIn() } throws ServerOfflineException()
            val viewModel = viewModel()

            // WHEN
            viewModel.signIn()
            advanceUntilIdle()

            // THEN
            assertEquals(GoogleSignInStatus.HubUnreachable, viewModel.uiState.value.status)
        }

    @Test
    fun `GIVEN the hub has no Google sign-in set up WHEN the member signs in THEN the screen says so`() =
        runTest(testDispatcher) {
            // GIVEN
            everySuspend { startSignIn() } throws GoogleSignInUnavailableException()
            val viewModel = viewModel()

            // WHEN
            viewModel.signIn()
            advanceUntilIdle()

            // THEN
            assertEquals(GoogleSignInStatus.Unavailable, viewModel.uiState.value.status)
        }

    @Test
    fun `GIVEN the hub answers badly WHEN the member signs in THEN it is a plain failure`() =
        runTest(testDispatcher) {
            // GIVEN
            everySuspend { startSignIn() } throws UpstreamGatewayException(statusCode = 500)
            val viewModel = viewModel()

            // WHEN
            viewModel.signIn()
            advanceUntilIdle()

            // THEN
            assertEquals(GoogleSignInStatus.Failed(CalendarSignInFailure.FAILED), viewModel.uiState.value.status)
        }

    private fun TestScope.inBrowser(): GoogleCalendarSignInViewModel {
        everySuspend { startSignIn() } returns googlePage
        return viewModel().also {
            it.signIn()
            advanceUntilIdle()
        }
    }
}
