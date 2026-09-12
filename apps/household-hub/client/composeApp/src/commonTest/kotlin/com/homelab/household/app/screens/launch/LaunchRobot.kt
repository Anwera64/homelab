package com.homelab.household.app.screens.launch

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.waitUntilExactlyOneExists

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

    fun seesTheHubIsOffline() = apply {
        seesText("Can't reach your hub")
        seesText("Try again")
    }

    fun seesTheHubSaid(message: String) = seesText(message)

    fun seesTheHubAddress(address: String) = seesText(address)

    fun tapsTryAgain() = apply {
        test.onNodeWithText("Try again").performClick()
    }

    private fun seesText(text: String) = apply {
        test.waitUntilExactlyOneExists(hasText(text), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithText(text).assertIsDisplayed()
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}

@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.onLaunch(block: LaunchRobot.() -> Unit) {
    LaunchRobot(this).block()
}
