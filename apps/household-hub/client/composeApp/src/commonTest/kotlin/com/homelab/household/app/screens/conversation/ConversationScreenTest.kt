package com.homelab.household.app.screens.conversation

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.conversation_composer_leave
import com.homelab.household.app.resources.conversation_composer_waiting
import com.homelab.household.app.resources.conversation_failed_line
import com.homelab.household.app.resources.conversation_failed_title
import com.homelab.household.app.resources.conversation_not_sent
import com.homelab.household.app.resources.conversation_reconnecting
import com.homelab.household.app.resources.conversation_retry
import com.homelab.household.app.resources.conversation_still_working
import com.homelab.household.app.resources.conversation_try_again
import com.homelab.household.app.resources.send_message
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
        onRetry: (String) -> Unit = {},
        onTryAgain: () -> Unit = {},
    ): @Composable () -> Unit =
        {
            StillTheme {
                ConversationContent(
                    state = state,
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
            setContent(conversation(stateNamed("Stream dropped, reconnecting"), onSend = { sent += it }))

            onNodeWithText(getString(Res.string.conversation_composer_waiting)).performTextInput("and another thing")
            onNodeWithContentDescription(getString(Res.string.send_message)).performClick()

            // A second send would answer 409; the placeholder says so instead of failing at it.
            assertEquals(emptyList(), sent)
        }

    @Test
    fun the_composer_sends_when_nothing_is_in_flight() =
        runComposeUiTest {
            val sent = mutableListOf<String>()
            setContent(conversation(stateNamed("A finished exchange"), onSend = { sent += it }))

            onNodeWithText("Message the Home Coordinator…").performTextInput("And Saturday?")
            onNodeWithContentDescription(getString(Res.string.send_message)).performClick()

            assertEquals(listOf("And Saturday?"), sent)
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
