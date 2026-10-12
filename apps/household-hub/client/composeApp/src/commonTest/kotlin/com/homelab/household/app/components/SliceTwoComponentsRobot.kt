package com.homelab.household.app.components

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilExactlyOneExists

/** The pieces the members screens are built from: a PIN, a code, a colour, a switch and a row. */
@OptIn(ExperimentalTestApi::class)
class SliceTwoComponentsRobot(
    private val test: ComposeUiTest,
) {
    fun typesIntoTheField(text: String) {
        test.waitUntilExactlyOneExists(hasSetTextAction(), timeoutMillis = WAIT_MILLIS)
        test.onNode(hasSetTextAction()).performTextInput(text)
    }

    /** Types into the control a caller described, as a screen reader would find it. */
    fun typesIntoTheControlDescribed(
        description: String,
        text: String,
    ) {
        described(description).performTextInput(text)
    }

    fun tapsTheControlDescribed(description: String) {
        described(description).performClick()
    }

    fun seesText(text: String) {
        labelled(text).assertIsDisplayed()
    }

    fun tapsText(text: String) {
        labelled(text).performClick()
    }

    private fun described(description: String): SemanticsNodeInteraction {
        test.waitUntilExactlyOneExists(hasContentDescription(description), timeoutMillis = WAIT_MILLIS)
        return test.onNodeWithContentDescription(description)
    }

    private fun labelled(text: String): SemanticsNodeInteraction {
        test.waitUntilExactlyOneExists(hasText(text), timeoutMillis = WAIT_MILLIS)
        return test.onNodeWithText(text)
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}

@OptIn(ExperimentalTestApi::class)
suspend fun ComposeUiTest.onSliceTwoComponents(block: suspend SliceTwoComponentsRobot.() -> Unit) {
    SliceTwoComponentsRobot(this).block()
}
