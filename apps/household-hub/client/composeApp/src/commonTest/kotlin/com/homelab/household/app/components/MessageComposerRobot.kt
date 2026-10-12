package com.homelab.household.app.components

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasImeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.compose.ui.text.input.ImeAction
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.send_message
import com.homelab.household.app.theme.DefaultSizes
import org.jetbrains.compose.resources.getString

/** The message composer: a field to type in, a send button, and the keyboard's own send key. */
@OptIn(ExperimentalTestApi::class)
class MessageComposerRobot(
    private val test: ComposeUiTest,
) {
    fun typesMessage(text: String) {
        field().performTextInput(text)
    }

    fun seesTypedMessage(text: String) {
        field().assertTextContains(text)
    }

    suspend fun tapsSend() {
        send().performClick()
    }

    /** Send is always there to be tapped, even with nothing typed. */
    suspend fun seesSendReadyToTap() {
        send().assertIsEnabled().assertHasClickAction()
    }

    suspend fun seesSendBigEnoughToHit() {
        send()
            .assertHeightIsAtLeast(DefaultSizes.touchTarget)
            .assertWidthIsAtLeast(DefaultSizes.touchTarget)
    }

    fun pressesTheKeyboardSendKey() {
        field().performImeAction()
    }

    fun seesTheKeyboardOffersSend() {
        field().assert(hasImeAction(ImeAction.Send))
    }

    fun seesPlaceholder(placeholder: String) {
        test.waitUntilExactlyOneExists(hasText(placeholder), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithText(placeholder).assertExists()
    }

    private fun field(): SemanticsNodeInteraction {
        test.waitUntilExactlyOneExists(hasSetTextAction(), timeoutMillis = WAIT_MILLIS)
        return test.onNode(hasSetTextAction())
    }

    private suspend fun send(): SemanticsNodeInteraction {
        val description = getString(Res.string.send_message)
        test.waitUntilExactlyOneExists(hasContentDescription(description), timeoutMillis = WAIT_MILLIS)
        return test.onNodeWithContentDescription(description)
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}

@OptIn(ExperimentalTestApi::class)
suspend fun ComposeUiTest.onMessageComposer(block: suspend MessageComposerRobot.() -> Unit) {
    MessageComposerRobot(this).block()
}
