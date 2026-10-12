package com.homelab.household.app.screens.calendarconnect

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.waitUntilExactlyOneExists
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.google_signin_again
import com.homelab.household.app.resources.google_signin_button
import com.homelab.household.app.resources.google_signin_denied
import com.homelab.household.app.resources.google_signin_step_two
import com.homelab.household.app.resources.google_signin_unavailable
import com.homelab.household.app.testing.FakeExternalApps
import com.homelab.household.app.testing.TestApp
import com.homelab.household.data.datasource.local.InMemorySessionStorage
import org.jetbrains.compose.resources.getString

/** Everything the Google sign-in screen says and does, named as the resources it reads. */
@OptIn(ExperimentalTestApi::class)
class GoogleCalendarSignInRobot(
    private val test: ComposeUiTest,
) {
    suspend fun tapsSignIn() {
        test.onNode(signInButton()).performClick()
    }

    /** The button is back, ready for another try. */
    suspend fun seesItReadyToSignIn() {
        test.waitUntilExactlyOneExists(signInButton(), timeoutMillis = WAIT_MILLIS)
    }

    suspend fun seesTheMemberDidNotShare() = seesText(getString(Res.string.google_signin_denied))

    suspend fun seesToSignInAgain() = seesText(getString(Res.string.google_signin_again))

    suspend fun doesNotSeeTheMemberDidNotShare() {
        test.onNodeWithText(getString(Res.string.google_signin_denied)).assertDoesNotExist()
    }

    suspend fun seesGoogleSignInIsUnavailable() = seesText(getString(Res.string.google_signin_unavailable))

    suspend fun seesTheStepsInTheBrowser() {
        val text = getString(Res.string.google_signin_step_two)
        test.waitUntilExactlyOneExists(hasText(text), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithText(text).assertExists()
    }

    /** The button, not the title above it that says the same. */
    private suspend fun signInButton(): SemanticsMatcher =
        hasClickAction() and hasText(getString(Res.string.google_signin_button))

    private fun seesText(text: String) {
        test.waitUntilExactlyOneExists(hasText(text), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithText(text).assertIsDisplayed()
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}

@OptIn(ExperimentalTestApi::class)
suspend fun ComposeUiTest.onGoogleCalendarSignIn(block: suspend GoogleCalendarSignInRobot.() -> Unit) {
    GoogleCalendarSignInRobot(this).block()
}

/** The Google sign-in screen, signed in, over a hub and a browser that answer however the test says. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.googleCalendarSignInScreen(
    hub: FakeCalendarHub,
    browser: FakeExternalApps,
    onConnect: () -> Unit = {},
) {
    setContent {
        TestApp(
            hub.engine,
            sessionStorage = InMemorySessionStorage().apply { saveTokens("signed-in-token") },
            externalApps = browser,
        ) {
            GoogleCalendarSignInScreen(onBack = {}, onConnect = onConnect)
        }
    }
}
