package com.homelab.household.app.components

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.waitUntilExactlyOneExists

/** What a bento card shows: its label, drawn in capitals, and whatever content it was given. */
@OptIn(ExperimentalTestApi::class)
class BentoCardRobot(
    private val test: ComposeUiTest,
) {
    /** The label is drawn upper-cased, so it is found however it was cased. */
    fun seesLabel(label: String) {
        test.waitUntilExactlyOneExists(hasText(label, ignoreCase = true), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithText(label, ignoreCase = true).assertIsDisplayed()
    }

    fun seesText(text: String) {
        test.waitUntilExactlyOneExists(hasText(text), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithText(text).assertIsDisplayed()
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}

@OptIn(ExperimentalTestApi::class)
suspend fun ComposeUiTest.onBentoCard(block: suspend BentoCardRobot.() -> Unit) {
    BentoCardRobot(this).block()
}
