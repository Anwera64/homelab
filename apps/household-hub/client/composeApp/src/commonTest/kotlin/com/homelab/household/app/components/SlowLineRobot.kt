package com.homelab.household.app.components

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.waitUntilExactlyOneExists
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.wait_slow_hub
import org.jetbrains.compose.resources.getString

/** The quiet line that says the hub is taking longer than usual. */
@OptIn(ExperimentalTestApi::class)
class SlowLineRobot(
    private val test: ComposeUiTest,
) {
    suspend fun seesTheHubIsSlow() {
        val text = getString(Res.string.wait_slow_hub)
        test.waitUntilExactlyOneExists(hasText(text), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithText(text).assertIsDisplayed()
    }

    suspend fun doesNotSeeTheHubIsSlow() {
        test.onNodeWithText(getString(Res.string.wait_slow_hub)).assertDoesNotExist()
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}

@OptIn(ExperimentalTestApi::class)
suspend fun ComposeUiTest.onSlowLine(block: suspend SlowLineRobot.() -> Unit) {
    SlowLineRobot(this).block()
}
