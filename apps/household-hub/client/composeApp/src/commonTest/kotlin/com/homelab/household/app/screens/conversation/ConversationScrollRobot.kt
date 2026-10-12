package com.homelab.household.app.screens.conversation

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.waitUntilExactlyOneExists

/** What the transcript shows as it fills, and the person scrolling back through it. */
@OptIn(ExperimentalTestApi::class)
class ConversationScrollRobot(
    private val test: ComposeUiTest,
) {
    /** Waits for [text] to arrive on screen. */
    fun seesText(text: String) {
        test.waitUntilExactlyOneExists(hasText(text), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithText(text).assertIsDisplayed()
    }

    /** Something already shown, with no waiting: for a screen that has settled. */
    fun seesItStillShown(text: String) {
        test.onNodeWithText(text).assertIsDisplayed()
    }

    fun doesNotSee(text: String) {
        test.onNodeWithText(text).assertDoesNotExist()
    }

    /** Waits for the list to have arrived at [text] without asserting more than that it exists. */
    fun waitsForText(text: String) {
        test.waitUntilExactlyOneExists(hasText(text), timeoutMillis = WAIT_MILLIS)
    }

    fun scrollsBackToTheStart() {
        test.onNodeWithTag(CONVERSATION_TRANSCRIPT_TAG).performScrollToIndex(0)
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}

@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.onConversationScroll(block: ConversationScrollRobot.() -> Unit) {
    ConversationScrollRobot(this).block()
}
