package com.homelab.household.app.screens.conversation

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.components.THINKING_DOTS_TAG
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.conversation_composer_leave
import com.homelab.household.app.resources.conversation_composer_waiting
import com.homelab.household.app.resources.conversation_failed_line
import com.homelab.household.app.resources.conversation_failed_title
import com.homelab.household.app.resources.conversation_not_sent
import com.homelab.household.app.resources.conversation_reconnecting
import com.homelab.household.app.resources.conversation_retry
import com.homelab.household.app.resources.conversation_sent
import com.homelab.household.app.resources.conversation_still_working
import com.homelab.household.app.resources.conversation_thought
import com.homelab.household.app.resources.conversation_try_again
import com.homelab.household.app.resources.send_message
import com.homelab.household.app.resources.tool_calendar_read_done
import com.homelab.household.app.resources.tool_calendar_read_failed
import com.homelab.household.app.resources.tool_calendar_read_running
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.presentation.chatsession.ChatSessionUiState
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A conversation, in each of the four ways a turn can end.
 *
 * The distinction these are all about: what failed, and therefore what is offered. A question that
 * never arrived is re-sent; an answer that never finished is asked for again; an answer still
 * being written is waited for and is not a failure at all.
 */
@OptIn(ExperimentalTestApi::class)
class ConversationScreenTest {
    private val provider = ConversationUiStateProvider()

    private fun stateNamed(name: String): ChatSessionUiState {
        val index = (0 until provider.values.count()).first { provider.getDisplayName(it) == name }
        return provider.values.elementAt(index)
    }

    /** The screen under test, with only the callbacks a given test cares about wired up. */
    private fun conversation(
        state: ChatSessionUiState,
        onSend: (String) -> Unit = {},
        onComposerTextChange: (String) -> Unit = {},
        onRetry: (String) -> Unit = {},
        onTryAgain: () -> Unit = {},
    ): @Composable () -> Unit =
        {
            StillTheme {
                ConversationContent(
                    state = state,
                    onComposerTextChange = onComposerTextChange,
                    onSend = onSend,
                    onRetry = onRetry,
                    onTryAgain = onTryAgain,
                    onBack = {},
                    memberName = "Emma",
                )
            }
        }

    @Test
    fun a_new_chat_greets_you_instead_of_showing_an_empty_transcript() =
        runComposeUiTest {
            setContent(conversation(stateNamed("New chat")))

            onNodeWithText("Evening, Emma").assertIsDisplayed()
            onNodeWithText("Schedules, meals, keeping the week straight.").assertIsDisplayed()
            onNodeWithText("Home Coordinator").assertIsDisplayed()
        }

    @Test
    fun a_question_that_never_arrived_is_marked_on_your_own_bubble_and_offers_to_resend() =
        runComposeUiTest {
            val resent = mutableListOf<String>()
            setContent(conversation(stateNamed("Never reached the hub"), onRetry = { resent += it }))

            onNodeWithText(getString(Res.string.conversation_not_sent)).assertIsDisplayed()
            onNodeWithText(getString(Res.string.conversation_retry)).performClick()

            assertEquals(listOf("Actually, make it 20:30"), resent)
        }

    @Test
    fun a_dropped_stream_keeps_the_half_answer_and_says_it_is_reconnecting() =
        runComposeUiTest {
            setContent(conversation(stateNamed("Stream dropped, reconnecting")))

            // The words that had arrived stay put; the indicator sits where they stopped.
            onNodeWithText("so if the boards aren't", substring = true).assertIsDisplayed()
            onNodeWithText(getString(Res.string.conversation_reconnecting)).assertIsDisplayed()
            onNodeWithText(getString(Res.string.conversation_composer_waiting)).assertIsDisplayed()
        }

    @Test
    fun the_question_is_not_blamed_when_the_stream_drops() =
        runComposeUiTest {
            setContent(conversation(stateNamed("Stream dropped, reconnecting")))

            // It arrived. Saying otherwise is what sent people into duplicate sends.
            onNodeWithText(getString(Res.string.conversation_not_sent)).assertDoesNotExist()
            onNodeWithText(getString(Res.string.conversation_retry)).assertDoesNotExist()
        }

    @Test
    fun past_the_active_wait_the_composer_says_you_can_leave() =
        runComposeUiTest {
            setContent(conversation(stateNamed("Still working after a minute")))

            onNodeWithText(getString(Res.string.conversation_still_working)).assertIsDisplayed()
            onNodeWithText(getString(Res.string.conversation_composer_leave)).assertIsDisplayed()
            // Not a failure: nothing here offers to do it again.
            onNodeWithText(getString(Res.string.conversation_try_again)).assertDoesNotExist()
        }

    @Test
    fun an_answer_that_failed_offers_a_fresh_one_and_says_the_question_arrived() =
        runComposeUiTest {
            var triedAgain = false
            setContent(conversation(stateNamed("The answer failed"), onTryAgain = { triedAgain = true }))

            onNodeWithText(getString(Res.string.conversation_failed_title)).assertIsDisplayed()
            onNodeWithText(getString(Res.string.conversation_failed_line)).assertIsDisplayed()
            onNodeWithText(getString(Res.string.conversation_try_again)).performClick()

            assertEquals(true, triedAgain)
        }

    @Test
    fun the_composer_does_not_send_while_an_answer_is_being_written() =
        runComposeUiTest {
            val sent = mutableListOf<String>()
            setContent(
                conversation(
                    stateNamed("Stream dropped, reconnecting").copy(composerText = "and another thing"),
                    onSend = { sent += it },
                ),
            )

            onNodeWithContentDescription(getString(Res.string.send_message)).performClick()

            // A second send would answer 409; the placeholder says so instead of failing at it.
            assertEquals(emptyList(), sent)
        }

    @Test
    fun the_composer_sends_when_nothing_is_in_flight() =
        runComposeUiTest {
            val sent = mutableListOf<String>()
            setContent(
                conversation(
                    stateNamed("A finished exchange").copy(composerText = "And Saturday?"),
                    onSend = { sent += it },
                ),
            )

            onNodeWithContentDescription(getString(Res.string.send_message)).performClick()

            assertEquals(listOf("And Saturday?"), sent)
        }

    @Test
    fun the_keyboard_send_key_goes_through_the_same_guard_as_the_button() =
        runComposeUiTest {
            val sent = mutableListOf<String>()
            setContent(
                conversation(
                    stateNamed("Stream dropped, reconnecting").copy(composerText = "and another thing"),
                    onSend = { sent += it },
                ),
            )

            onNode(hasSetTextAction()).performImeAction()

            // Otherwise Enter would be a way round the rule the send button obeys.
            assertEquals(emptyList(), sent)
        }

    @Test
    fun the_keyboard_send_key_sends_when_nothing_is_in_flight() =
        runComposeUiTest {
            val sent = mutableListOf<String>()
            setContent(
                conversation(
                    stateNamed("A finished exchange").copy(composerText = "And Saturday?"),
                    onSend = { sent += it },
                ),
            )

            onNode(hasSetTextAction()).performImeAction()

            assertEquals(listOf("And Saturday?"), sent)
        }

    @Test
    fun a_send_that_failed_says_so_and_keeps_what_you_typed() =
        runComposeUiTest {
            setContent(
                conversation(
                    stateNamed("A finished exchange").copy(
                        composerText = "Hi",
                        errorMessage = "Can't reach your hub",
                    ),
                ),
            )

            // Silence here is what made a failed send look like nothing happening at all.
            onNodeWithText("Can't reach your hub").assertIsDisplayed()
            onNode(hasSetTextAction()).assertTextContains("Hi")
        }

    /**
     * The silence between sending and the first word, which is the longest in the app: a model
     * being loaded into memory, or one that thinks before it writes. It used to be an empty bubble
     * and nothing else, which reads as nothing having happened at all.
     */
    @Test
    fun before_the_first_word_arrives_the_screen_says_it_is_thinking() =
        runComposeUiTest {
            setContent(conversation(stateNamed("Thinking, before the first word")))

            onNodeWithTag(THINKING_DOTS_TAG).assertIsDisplayed()
        }

    @Test
    fun the_first_word_takes_the_dots_place() =
        runComposeUiTest {
            setContent(conversation(stateNamed("Answering")))

            // Words are their own sign that something is happening; two at once is noise.
            onNodeWithTag(THINKING_DOTS_TAG).assertDoesNotExist()
            onNodeWithText("Three things, in order of how much ", substring = true).assertIsDisplayed()
        }

    // ---- a turn you can watch ----------------------------------------------

    @Test
    fun the_newest_question_says_it_was_sent() =
        runComposeUiTest {
            setContent(conversation(stateNamed("A finished exchange")))

            onNodeWithText(getString(Res.string.conversation_sent)).assertIsDisplayed()
        }

    @Test
    fun a_question_that_never_landed_does_not_claim_to_have_been_sent() =
        runComposeUiTest {
            setContent(conversation(stateNamed("Never reached the hub")))

            onNodeWithText(getString(Res.string.conversation_sent)).assertDoesNotExist()
        }

    @Test
    fun while_an_answer_is_being_written_the_composer_says_it_is_waiting() =
        runComposeUiTest {
            setContent(conversation(stateNamed("Thinking, before the first word")))

            // It used to keep its idle words while refusing every send, which read as broken.
            onNodeWithText(getString(Res.string.conversation_composer_waiting)).assertIsDisplayed()
        }

    @Test
    fun a_model_thinking_out_loud_is_shown_under_the_dots() =
        runComposeUiTest {
            setContent(conversation(stateNamed("Thinking out loud")))

            onNodeWithTag(THINKING_DOTS_TAG).assertIsDisplayed()
            // Only its newest words — the start has scrolled away.
            onNodeWithText("then list the events in order.", substring = true).assertIsDisplayed()
            onNodeWithText("Okay, the user is asking", substring = true).assertDoesNotExist()
        }

    @Test
    fun a_tool_is_named_in_words_while_it_runs() =
        runComposeUiTest {
            setContent(conversation(stateNamed("Using a tool")))

            onNodeWithText(getString(Res.string.tool_calendar_read_running)).assertIsDisplayed()
            onNodeWithText(getString(Res.string.conversation_thought, 3)).assertIsDisplayed()
            onNodeWithText("calendar_read", substring = true).assertDoesNotExist()
        }

    @Test
    fun the_trail_sits_above_an_answer_as_it_arrives() =
        runComposeUiTest {
            setContent(conversation(stateNamed("The answer arrives, with its trail")))

            onNodeWithText(getString(Res.string.conversation_thought, 4)).assertIsDisplayed()
            onNodeWithText(getString(Res.string.tool_calendar_read_done)).assertIsDisplayed()
        }

    @Test
    fun a_finished_answer_keeps_its_trail() =
        runComposeUiTest {
            setContent(conversation(stateNamed("A finished answer and its trail")))

            onNodeWithText(getString(Res.string.tool_calendar_read_done)).assertIsDisplayed()
        }

    @Test
    fun a_tool_that_could_not_run_says_so_in_the_trail() =
        runComposeUiTest {
            setContent(conversation(stateNamed("A tool that couldn't run")))

            onNodeWithText(getString(Res.string.tool_calendar_read_failed)).assertIsDisplayed()
        }

    /** A failed turn has a card of its own to show; the dots would say it was still coming. */
    @Test
    fun an_answer_that_failed_shows_no_dots() =
        runComposeUiTest {
            setContent(conversation(stateNamed("The answer failed")))

            onNodeWithTag(THINKING_DOTS_TAG).assertDoesNotExist()
        }

    @Test
    fun every_previewed_state_draws() {
        provider.values.toList().forEach { state ->
            runComposeUiTest {
                setContent(conversation(state))

                onNodeWithText("Home Coordinator").assertIsDisplayed()
            }
        }
    }
}
