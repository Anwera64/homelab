package com.homelab.household.app.screens.conversation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.presentation.chatsession.ChatSessionUiState
import kotlin.test.Test

/**
 * A long answer arrives faster than anyone reads it, and it arrives at the bottom.
 *
 * Without the list following it, the words appear below the fold and the screen sits still while
 * the agent talks — which looks, from the sofa, exactly like nothing happening.
 */
@OptIn(ExperimentalTestApi::class)
class ConversationScrollTest {
    private val provider = ConversationUiStateProvider()

    private fun stateNamed(name: String): ChatSessionUiState {
        val index = (0 until provider.values.count()).first { provider.getDisplayName(it) == name }
        return provider.values.elementAt(index)
    }

    private fun conversation(state: ChatSessionUiState): @Composable () -> Unit =
        {
            StillTheme {
                // A phone, so a long answer actually outgrows the viewport. The test window is
                // otherwise large enough to show everything, and would prove nothing.
                Box(modifier = Modifier.size(PHONE_WIDTH, PHONE_HEIGHT)) {
                    ConversationContent(
                        state = state,
                        onComposerTextChange = {},
                        onSend = {},
                        onRetry = {},
                        onTryAgain = {},
                        onBack = {},
                        memberName = "Emma",
                    )
                }
            }
        }

    private companion object {
        val PHONE_WIDTH = 390.dp
        val PHONE_HEIGHT = 844.dp
    }

    @Test
    fun a_long_answer_scrolls_the_question_off_the_top() =
        runComposeUiTest {
            setContent(conversation(stateNamed("A long answer arriving")))

            // The answer is one text node, so "is the newest sentence on screen" cannot tell
            // scrolled from not — the node counts as displayed the moment any of it is. What the
            // question does is the honest signal: following the answer pushes it off the top, far
            // enough that the list stops composing it at all.
            onNodeWithText("What's left before Friday?").assertDoesNotExist()
        }

    @Test
    fun opening_a_long_conversation_starts_at_the_newest_message() =
        runComposeUiTest {
            setContent(conversation(stateNamed("A conversation opened again")))

            onNodeWithText("Answer 20,", substring = true).assertIsDisplayed()
            onNodeWithText("Question 1 of a long-running conversation").assertDoesNotExist()
        }

    @Test
    fun a_conversation_that_fits_does_not_scroll_anything_away() =
        runComposeUiTest {
            setContent(conversation(stateNamed("A finished exchange")))

            onNodeWithText("What's left before Friday?").assertIsDisplayed()
        }
}
