package com.homelab.household.app.screens.launch

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.waitUntilExactlyOneExists
import com.homelab.household.app.testing.TestApp

/**
 * Everything the launch screen says and does, named once.
 *
 * Each assertion waits for its text first: the hub answers on its own coroutine, so `waitForIdle`
 * — which only waits for Compose — can run before the answer has arrived.
 */
@OptIn(ExperimentalTestApi::class)
class LaunchRobot(private val test: ComposeUiTest) {

    fun seesItReachingTheHub() = seesText("Reaching your hub…")

    fun seesTheHubIsReady(members: Int) = apply {
        seesText("Your hub is ready")
        seesText(if (members == 1) "1 member" else "$members members")
    }

    fun seesNobodyLivesHereYet() = seesText("Nobody lives here yet")

    /**
     * Unreachable, not merely failed: both draw the same title and button, so the explanation
     * is the only thing that tells them apart.
     */
    fun seesTheHubIsOffline() = apply {
        seesText("Can't reach your hub")
        seesTextContaining("nothing answered at your home server")
        seesSomethingToRetry()
    }

    fun seesTheHubSaid(message: String) = seesText(message)

    fun seesSomethingToRetry() = seesText("Try again")

    fun seesTheHubAddress(address: String) = seesText(address)

    fun tapsTryAgain() = apply {
        test.onNodeWithText("Try again").performClick()
    }

    private fun seesText(text: String) = apply {
        test.waitUntilExactlyOneExists(hasText(text), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithText(text).assertIsDisplayed()
    }

    private fun seesTextContaining(text: String) = apply {
        test.waitUntilExactlyOneExists(hasText(text, substring = true), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithText(text, substring = true).assertIsDisplayed()
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}

@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.onLaunch(block: LaunchRobot.() -> Unit) {
    LaunchRobot(this).block()
}

/** The launch screen over a hub that answers however the test says. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.launchScreen(
    hub: FakeLaunchHub,
    onSignIn: () -> Unit = {},
    onFirstRun: () -> Unit = {}
) {
    setContent {
        TestApp(hub.engine) {
            LaunchScreen(onSignIn = onSignIn, onFirstRun = onFirstRun)
        }
    }
}
