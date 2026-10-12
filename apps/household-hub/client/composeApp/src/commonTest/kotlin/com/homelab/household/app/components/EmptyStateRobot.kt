package com.homelab.household.app.components

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.waitUntilExactlyOneExists

/** What an empty state shows: its icon on a tile, a title, a line, and an action only when it was given one. */
@OptIn(ExperimentalTestApi::class)
class EmptyStateRobot(
    private val test: ComposeUiTest,
) {
    fun seesTheIcon() {
        test.waitUntilExactlyOneExists(hasTestTag(EMPTY_STATE_ICON_TAG), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithTag(EMPTY_STATE_ICON_TAG).assertIsDisplayed()
    }

    fun seesText(text: String) {
        test.waitUntilExactlyOneExists(hasText(text), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithText(text).assertIsDisplayed()
    }

    fun tapsTheAction(label: String) {
        seesText(label)
        test.onNodeWithText(label).performClick()
    }

    fun doesNotSeeTheAction(label: String) {
        test.onNodeWithText(label).assertDoesNotExist()
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}

@OptIn(ExperimentalTestApi::class)
suspend fun ComposeUiTest.onEmptyState(block: suspend EmptyStateRobot.() -> Unit) {
    EmptyStateRobot(this).block()
}
