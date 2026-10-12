package com.homelab.household.app.components

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilExactlyOneExists
import kotlin.test.assertTrue

/** A text field and the error that may sit under it. */
@OptIn(ExperimentalTestApi::class)
class HearthTextFieldRobot(
    private val test: ComposeUiTest,
) {
    /** The error is shown below the field, and the field itself is marked as being in error. */
    fun seesTheErrorBelowTheField(error: String) {
        val field = field()
        test.waitUntilExactlyOneExists(hasText(error), timeoutMillis = WAIT_MILLIS)
        val errorNode = test.onNodeWithText(error).assertIsDisplayed()

        field.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
        val fieldBottom = field.getUnclippedBoundsInRoot().bottom
        val errorTop = errorNode.getUnclippedBoundsInRoot().top
        assertTrue(errorTop >= fieldBottom, "Error ($errorTop) should sit below the field ($fieldBottom)")
    }

    fun seesTheFieldNotMarkedAsAnError() {
        field().assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))
    }

    fun typesIntoTheField(text: String) {
        field().performTextInput(text)
    }

    fun seesTheFieldContaining(text: String) {
        field().assertTextContains(text)
    }

    fun seesText(text: String) {
        test.waitUntilExactlyOneExists(hasText(text), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithText(text).assertIsDisplayed()
    }

    private fun field(): SemanticsNodeInteraction {
        test.waitUntilExactlyOneExists(hasSetTextAction(), timeoutMillis = WAIT_MILLIS)
        return test.onNode(hasSetTextAction())
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}

@OptIn(ExperimentalTestApi::class)
suspend fun ComposeUiTest.onHearthTextField(block: suspend HearthTextFieldRobot.() -> Unit) {
    HearthTextFieldRobot(this).block()
}
