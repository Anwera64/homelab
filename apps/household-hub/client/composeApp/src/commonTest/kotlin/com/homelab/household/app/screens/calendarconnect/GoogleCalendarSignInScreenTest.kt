package com.homelab.household.app.screens.calendarconnect

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.waitUntilExactlyOneExists
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.google_signin_again
import com.homelab.household.app.resources.google_signin_button
import com.homelab.household.app.resources.google_signin_denied
import com.homelab.household.app.resources.google_signin_step_two
import com.homelab.household.app.resources.google_signin_unavailable
import com.homelab.household.app.testing.FakeExternalApps
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.app.testing.TestApp
import com.homelab.household.app.testing.runScreenTest
import com.homelab.household.data.datasource.local.InMemorySessionStorage
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals

/** Signing in with Google, with the real stack under it; only the hub and the browser are faked. */
@OptIn(ExperimentalTestApi::class)
class GoogleCalendarSignInScreenTest {
    private val wait = 5_000L

    /** A phone with somebody signed in on it: this screen is behind a token. */
    private fun signedIn() = InMemorySessionStorage().apply { saveTokens("signed-in-token") }

    /** The button, not the title above it that says the same. */
    private suspend fun signInButton(): SemanticsMatcher =
        hasClickAction() and hasText(getString(Res.string.google_signin_button))

    @Test
    fun `GIVEN Google shares the calendar WHEN the member signs in THEN Google's page opens and the screen hands back`() {
        // GIVEN
        val hub = FakeCalendarHub()
        val browser = FakeExternalApps().apply { browserReturnsTo = "hyggehub://calendar/connected" }
        var connected = 0

        runScreenTest {
            setContent {
                TestApp(hub.engine, sessionStorage = signedIn(), externalApps = browser) {
                    GoogleCalendarSignInScreen(onBack = {}, onConnect = { connected++ })
                }
            }

            // WHEN
            onNode(signInButton()).performClick()

            // THEN
            waitUntil(timeoutMillis = wait) { connected == 1 }
        }
        assertEquals(listOf(FakeCalendarHub.GOOGLE_PAGE), browser.pagesOpened)
    }

    @Test
    fun `GIVEN the member did not share their calendar WHEN the browser comes back THEN the screen says so and offers to sign in again`() {
        // GIVEN
        val hub = FakeCalendarHub()
        val browser = FakeExternalApps().apply { browserReturnsTo = "hyggehub://calendar/failed?reason=denied" }
        var connected = 0

        runScreenTest {
            setContent {
                TestApp(hub.engine, sessionStorage = signedIn(), externalApps = browser) {
                    GoogleCalendarSignInScreen(onBack = {}, onConnect = { connected++ })
                }
            }

            // WHEN
            onNode(signInButton()).performClick()

            // THEN
            waitUntilExactlyOneExists(hasText(getString(Res.string.google_signin_denied)), timeoutMillis = wait)
            onNodeWithText(getString(Res.string.google_signin_again)).assertIsDisplayed()
        }
        assertEquals(0, connected)
    }

    @Test
    fun `GIVEN the member closes the browser WHEN it comes back empty THEN the screen is ready to sign in again with no error`() {
        // GIVEN
        val hub = FakeCalendarHub()
        val browser = FakeExternalApps().apply { browserReturnsTo = null }

        runScreenTest {
            setContent {
                TestApp(hub.engine, sessionStorage = signedIn(), externalApps = browser) {
                    GoogleCalendarSignInScreen(onBack = {}, onConnect = {})
                }
            }

            // WHEN
            onNode(signInButton()).performClick()

            // THEN
            waitUntil(timeoutMillis = wait) { browser.pagesOpened.size == 1 }
            waitUntilExactlyOneExists(signInButton(), timeoutMillis = wait)
            onNodeWithText(getString(Res.string.google_signin_denied)).assertDoesNotExist()
        }
    }

    @Test
    fun `GIVEN the hub has no Google sign-in set up WHEN the member signs in THEN the screen says so and no browser opens`() {
        // GIVEN
        val hub = FakeCalendarHub().apply { hasNoGoogleSignIn() }
        val browser = FakeExternalApps()

        runScreenTest {
            setContent {
                TestApp(hub.engine, sessionStorage = signedIn(), externalApps = browser) {
                    GoogleCalendarSignInScreen(onBack = {}, onConnect = {})
                }
            }

            // WHEN
            onNode(signInButton()).performClick()

            // THEN
            waitUntilExactlyOneExists(hasText(getString(Res.string.google_signin_unavailable)), timeoutMillis = wait)
        }
        assertEquals(emptyList(), browser.pagesOpened)
    }

    @Test
    fun `GIVEN every previewed state WHEN it is drawn THEN the steps in the browser are there`() {
        // GIVEN
        val states = GoogleSignInUiStateProvider().values.toList()

        states.forEach { state ->
            runComposeUiTest {
                // WHEN
                setContent { StillTheme { GoogleCalendarSignInContent(state = state, onSignIn = {}, onBack = {}) } }

                // THEN
                onNodeWithText(getString(Res.string.google_signin_step_two)).assertExists()
            }
        }
    }
}
