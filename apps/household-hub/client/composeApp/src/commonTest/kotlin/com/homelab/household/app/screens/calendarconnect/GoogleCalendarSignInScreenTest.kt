package com.homelab.household.app.screens.calendarconnect

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.testing.FakeExternalApps
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.app.testing.runScreenTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Signing in with Google, with the real stack under it; only the hub and the browser are faked. */
@OptIn(ExperimentalTestApi::class)
class GoogleCalendarSignInScreenTest {
    private val wait = 5_000L

    @Test
    fun `GIVEN Google shares the calendar WHEN the member signs in THEN Google's page opens and the screen hands back`() {
        val hub = FakeCalendarHub()
        val browser = FakeExternalApps().apply { browserReturnsTo = "hyggehub://calendar/connected" }
        var connected = 0

        runScreenTest {
            googleCalendarSignInScreen(hub, browser, onConnect = { connected++ })

            onGoogleCalendarSignIn { tapsSignIn() }

            waitUntil(timeoutMillis = wait) { connected == 1 }
        }
        assertEquals(listOf(FakeCalendarHub.GOOGLE_PAGE), browser.pagesOpened)
    }

    @Test
    fun `GIVEN the member did not share their calendar WHEN the browser comes back THEN the screen says so and offers to sign in again`() {
        val hub = FakeCalendarHub()
        val browser = FakeExternalApps().apply { browserReturnsTo = "hyggehub://calendar/failed?reason=denied" }
        var connected = 0

        runScreenTest {
            googleCalendarSignInScreen(hub, browser, onConnect = { connected++ })

            onGoogleCalendarSignIn {
                tapsSignIn()

                seesTheMemberDidNotShare()
                seesToSignInAgain()
            }
        }
        assertEquals(0, connected)
    }

    @Test
    fun `GIVEN the member closes the browser WHEN it comes back empty THEN the screen is ready to sign in again with no error`() {
        val hub = FakeCalendarHub()
        val browser = FakeExternalApps().apply { browserReturnsTo = null }

        runScreenTest {
            googleCalendarSignInScreen(hub, browser)

            onGoogleCalendarSignIn { tapsSignIn() }

            waitUntil(timeoutMillis = wait) { browser.pagesOpened.size == 1 }
            onGoogleCalendarSignIn {
                seesItReadyToSignIn()
                doesNotSeeTheMemberDidNotShare()
            }
        }
    }

    @Test
    fun `GIVEN the hub has no Google sign-in set up WHEN the member signs in THEN the screen says so and no browser opens`() {
        val hub = FakeCalendarHub().apply { hasNoGoogleSignIn() }
        val browser = FakeExternalApps()

        runScreenTest {
            googleCalendarSignInScreen(hub, browser)

            onGoogleCalendarSignIn {
                tapsSignIn()

                seesGoogleSignInIsUnavailable()
            }
        }
        assertEquals(emptyList(), browser.pagesOpened)
    }

    @Test
    fun `GIVEN every previewed state WHEN it is drawn THEN the steps in the browser are there`() {
        val states = GoogleSignInUiStateProvider().values.toList()

        states.forEach { state ->
            runComposeUiTest {
                setContent { StillTheme { GoogleCalendarSignInContent(state = state, onSignIn = {}, onBack = {}) } }

                onGoogleCalendarSignIn { seesTheStepsInTheBrowser() }
            }
        }
    }
}
