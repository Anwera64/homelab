package com.homelab.household.app.components

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.waitUntilExactlyOneExists

/** What a chip shows: its label. */
@OptIn(ExperimentalTestApi::class)
class HearthChipRobot(
    private val test: ComposeUiTest,
) {
    fun seesLabel(label: String) {
        test.waitUntilExactlyOneExists(hasText(label), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithText(label).assertIsDisplayed()
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}

@OptIn(ExperimentalTestApi::class)
suspend fun ComposeUiTest.onHearthChip(block: suspend HearthChipRobot.() -> Unit) {
    HearthChipRobot(this).block()
}
