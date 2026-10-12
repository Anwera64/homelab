package com.homelab.household.app.components

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.waitUntilExactlyOneExists

/** The quiet record a tool leaves above an answer. */
@OptIn(ExperimentalTestApi::class)
class ToolRecordLineRobot(
    private val test: ComposeUiTest,
) {
    fun seesRecord(text: String) {
        record(text).assertIsDisplayed()
    }

    fun doesNotSeeItCanBeOpened(text: String) {
        test.onNode(hasText(text) and hasClickAction()).assertDoesNotExist()
    }

    fun tapsRecordThatCanBeOpened(text: String) {
        record(text).assertHasClickAction().performClick()
    }

    private fun record(text: String): SemanticsNodeInteraction {
        test.waitUntilExactlyOneExists(hasText(text), timeoutMillis = WAIT_MILLIS)
        return test.onNodeWithText(text)
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}

@OptIn(ExperimentalTestApi::class)
suspend fun ComposeUiTest.onToolRecordLine(block: suspend ToolRecordLineRobot.() -> Unit) {
    ToolRecordLineRobot(this).block()
}
