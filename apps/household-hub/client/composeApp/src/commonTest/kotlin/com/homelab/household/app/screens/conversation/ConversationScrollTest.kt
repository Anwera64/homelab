package com.homelab.household.app.screens.conversation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.compose.ui.unit.dp
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.domain.model.ChatMessage
import com.homelab.household.domain.model.MessageRole
import com.homelab.household.presentation.chatsession.ChatSessionUiState
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * A long answer arrives faster than anyone reads it, and it arrives at the bottom.
 *
 * Without the list following it, the words appear below the fold and the screen sits still while
 * the agent talks — which looks, from the sofa, exactly like nothing happening. The same goes for
 * a chat you have been having for weeks: it has to open on the newest message, not the first.
 *
 * These hand the composition a `StandardTestDispatcher` deliberately. It queues coroutines rather
 * than running them eagerly, which is what the phone does. Under the default eager dispatcher the
 * scrolling `LaunchedEffect` gets to look at the list before it has been measured around the new
 * messages, every test here passes against a screen that follows nothing, and that is how this
 * shipped broken.
 */
@OptIn(ExperimentalTestApi::class)
class ConversationScrollTest {
    private val provider = ConversationUiStateProvider()

    private fun stateNamed(name: String): ChatSessionUiState {
        val index = (0 until provider.values.count()).first { provider.getDisplayName(it) == name }
        return provider.values.elementAt(index)
    }

    private fun conversation(state: ChatSessionUiState): @Composable () -> Unit = growing { state }

    /** The screen reading its state each recomposition, so a message can land while it is open. */
    private fun growing(state: () -> ChatSessionUiState): @Composable () -> Unit =
        {
            StillTheme {
                // A phone, so a long answer actually outgrows the viewport. The test window is
                // otherwise large enough to show everything, and would prove nothing.
                Box(modifier = Modifier.size(PHONE_WIDTH, PHONE_HEIGHT)) {
                    ConversationContent(
                        state = state(),
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

    /**
     * A few more turns, and then the newest answer.
     *
     * More than one message, and more than a line each, because a list parked at its very end has
     * empty space below it: a single short answer lands in that space and is on screen whether or
     * not anything followed it, which is no test at all.
     */
    private fun ChatSessionUiState.andThen(newest: String) =
        copy(
            messages =
                messages +
                    (1..6).map {
                        ChatMessage(
                            id = "later-$it",
                            sessionId = "s-1",
                            role = if (it % 2 == 0) MessageRole.ASSISTANT else MessageRole.USER,
                            content = "Turn $it after the fold, with enough in it to fill a line or two of the screen.",
                        )
                    } +
                    ChatMessage(id = "newest", sessionId = "s-1", role = MessageRole.ASSISTANT, content = newest),
        )

    @Test
    fun a_long_answer_scrolls_the_question_off_the_top() =
        runComposeUiTest(effectContext = StandardTestDispatcher()) {
            setContent(conversation(stateNamed("A long answer arriving")))
            waitForIdle()

            // The answer is one text node, so "is the newest sentence on screen" cannot tell
            // scrolled from not — the node counts as displayed the moment any of it is. What the
            // question does is the honest signal: following the answer pushes it off the top, far
            // enough that the list stops composing it at all.
            onNodeWithText("What's left before Friday?").assertDoesNotExist()
        }

    @Test
    fun opening_a_long_conversation_starts_at_the_newest_message() =
        runComposeUiTest(effectContext = StandardTestDispatcher()) {
            // As the screen really opens: the greeting is drawn first and the transcript lands a
            // moment later, so the list is composed by a recomposition, not by the first pass.
            val opened = stateNamed("A conversation opened again")
            var state by mutableStateOf(opened.copy(messages = emptyList()))
            setContent(growing { state })

            state = opened

            waitUntilExactlyOneExists(hasText(NEWEST_ANSWER), timeoutMillis = WAIT_MILLIS)
            onNodeWithText(OLDEST_QUESTION).assertDoesNotExist()
        }

    @Test
    fun the_transcript_follows_an_answer_down() =
        runComposeUiTest(effectContext = StandardTestDispatcher()) {
            var state by mutableStateOf(stateNamed("A conversation opened again"))
            setContent(growing { state })
            waitUntilExactlyOneExists(hasText(NEWEST_ANSWER), timeoutMillis = WAIT_MILLIS)

            state = state.andThen(LATEST_ANSWER)

            waitUntilExactlyOneExists(hasText(LATEST_ANSWER), timeoutMillis = WAIT_MILLIS)
            onNodeWithText(LATEST_ANSWER).assertIsDisplayed()
        }

    @Test
    fun an_answer_arriving_does_not_yank_you_back_from_what_you_were_re_reading() =
        runComposeUiTest(effectContext = StandardTestDispatcher()) {
            var state by mutableStateOf(stateNamed("A conversation opened again"))
            setContent(growing { state })
            waitUntilExactlyOneExists(hasText(NEWEST_ANSWER), timeoutMillis = WAIT_MILLIS)

            onNodeWithTag(CONVERSATION_TRANSCRIPT_TAG).performScrollToIndex(0)
            waitUntilExactlyOneExists(hasText(OLDEST_QUESTION), timeoutMillis = WAIT_MILLIS)

            state = state.andThen(LATEST_ANSWER)
            waitForIdle()

            // Following unconditionally would make a long answer impossible to read back while it
            // is still being written.
            onNodeWithText(OLDEST_QUESTION).assertIsDisplayed()
            onNodeWithText(LATEST_ANSWER).assertDoesNotExist()
        }

    @Test
    fun a_conversation_that_fits_does_not_scroll_anything_away() =
        runComposeUiTest(effectContext = StandardTestDispatcher()) {
            setContent(conversation(stateNamed("A finished exchange")))
            waitForIdle()

            onNodeWithText("What's left before Friday?").assertIsDisplayed()
        }

    private companion object {
        val PHONE_WIDTH = 390.dp
        val PHONE_HEIGHT = 844.dp
        const val WAIT_MILLIS = 5_000L
        const val NEWEST_ANSWER = "Answer 20, at some length to fill the screen."
        const val OLDEST_QUESTION = "Question 1 of a long-running conversation"
        const val LATEST_ANSWER = "One more answer, after the fold."
    }
}
