package com.homelab.household.app.components

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.waitUntilExactlyOneExists

/** The top bar: a back button, described the way its caller described it, and an optional title. */
@OptIn(ExperimentalTestApi::class)
class HearthTopBarRobot(
    private val test: ComposeUiTest,
) {
    fun seesBack(description: String) {
        back(description).assertIsDisplayed()
    }

    fun tapsBack(description: String) {
        back(description).performClick()
    }

    fun seesTitle(title: String) {
        test.waitUntilExactlyOneExists(hasText(title), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithText(title).assertIsDisplayed()
    }

    private fun back(description: String): SemanticsNodeInteraction {
        test.waitUntilExactlyOneExists(hasContentDescription(description), timeoutMillis = WAIT_MILLIS)
        return test.onNodeWithContentDescription(description)
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}

@OptIn(ExperimentalTestApi::class)
suspend fun ComposeUiTest.onHearthTopBar(block: suspend HearthTopBarRobot.() -> Unit) {
    HearthTopBarRobot(this).block()
}
