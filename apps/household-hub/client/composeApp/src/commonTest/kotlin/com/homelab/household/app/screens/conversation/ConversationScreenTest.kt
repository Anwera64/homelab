package com.homelab.household.app.screens.conversation

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import com.homelab.household.app.components.NOT_SENT_GLYPH_TAG
import com.homelab.household.app.components.PICKER_CONFIRM_TAG
import com.homelab.household.app.components.PICKER_DISMISS_TAG
import com.homelab.household.app.components.RETRY_GLYPH_TAG
import com.homelab.household.app.components.SENT_GLYPH_TAG
import com.homelab.household.app.components.THINKING_DOTS_TAG
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.agent_picker_failed_title
import com.homelab.household.app.resources.agent_picker_title
import com.homelab.household.app.resources.conversation_change_agent
import com.homelab.household.app.resources.conversation_composer_leave
import com.homelab.household.app.resources.conversation_composer_waiting
import com.homelab.household.app.resources.conversation_failed_line
import com.homelab.household.app.resources.conversation_failed_title
import com.homelab.household.app.resources.conversation_not_sent
import com.homelab.household.app.resources.conversation_reconnecting
import com.homelab.household.app.resources.conversation_reconnecting_detail
import com.homelab.household.app.resources.conversation_retry
import com.homelab.household.app.resources.conversation_sent
import com.homelab.household.app.resources.conversation_steps
import com.homelab.household.app.resources.conversation_still_working
import com.homelab.household.app.resources.conversation_still_working_detail
import com.homelab.household.app.resources.conversation_thinking
import com.homelab.household.app.resources.conversation_thought
import com.homelab.household.app.resources.conversation_try_again
import com.homelab.household.app.resources.field_changed
import com.homelab.household.app.resources.send_message
import com.homelab.household.app.resources.tool_automatic_undo
import com.homelab.household.app.resources.tool_calendar_add_automatic_ask
import com.homelab.household.app.resources.tool_calendar_add_declined
import com.homelab.household.app.resources.tool_calendar_add_failed
import com.homelab.household.app.resources.tool_calendar_add_now_automatic
import com.homelab.household.app.resources.tool_calendar_read_done
import com.homelab.household.app.resources.tool_calendar_read_failed
import com.homelab.household.app.resources.tool_calendar_read_running
import com.homelab.household.app.resources.tool_calendar_remove_card
import com.homelab.household.app.resources.tool_card_approve
import com.homelab.household.app.resources.tool_card_approve_with_changes
import com.homelab.household.app.resources.tool_card_cancel
import com.homelab.household.app.resources.tool_card_decline
import com.homelab.household.app.resources.tool_card_edit
import com.homelab.household.app.resources.tool_card_field_day
import com.homelab.household.app.resources.tool_card_field_empty
import com.homelab.household.app.resources.tool_card_field_time
import com.homelab.household.app.resources.tool_card_hold
import com.homelab.household.app.resources.tool_card_keep
import com.homelab.household.app.resources.tool_card_remove
import com.homelab.household.app.resources.tool_fix_ask_again
import com.homelab.household.app.resources.tool_fix_calendar_fixed_title
import com.homelab.household.app.resources.tool_fix_calendar_rejected_detail
import com.homelab.household.app.resources.tool_fix_calendar_rejected_title
import com.homelab.household.app.resources.tool_fix_nothing_added
import com.homelab.household.app.resources.tool_fix_reconnect_calendar
import com.homelab.household.app.resources.tool_read_page_done
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.domain.model.EventMoment
import com.homelab.household.domain.model.ProposalDetails
import com.homelab.household.presentation.chatsession.ChatSessionUiState
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        onSelectAgent: (String) -> Unit = {},
        onRetryAgents: () -> Unit = {},
        onDecide: (String, Boolean, ProposalDetails?) -> Unit = { _, _, _ -> },
        onConnectCalendar: () -> Unit = {},
        onApproveAutomatically: (String) -> Unit = {},
        onUndoAutomatic: () -> Unit = {},
    ): @Composable () -> Unit =
        {
            StillTheme {
                ConversationContent(
                    state = state,
                    onComposerTextChange = onComposerTextChange,
                    onSend = onSend,
                    onRetry = onRetry,
                    onTryAgain = onTryAgain,
                    onSelectAgent = onSelectAgent,
                    onRetryAgents = onRetryAgents,
                    onDecide = onDecide,
                    onConnectCalendar = onConnectCalendar,
                    onApproveAutomatically = onApproveAutomatically,
                    onUndoAutomatic = onUndoAutomatic,
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

    /**
     * iOS has no back button to hide the keyboard, and the composer's return key sends. Tapping the
     * conversation, away from the composer, is how Messages lets it go, so it must clear focus.
     */
    @Test
    fun `GIVEN the composer focused WHEN the transcript is tapped THEN the keyboard goes away`() =
        runComposeUiTest {
            setContent(conversation(stateNamed("A finished exchange")))

            onNode(hasSetTextAction()).performClick()
            onNode(hasSetTextAction()).assertIsFocused()
            onNodeWithText("The panel review is tomorrow at 14:30.").performTouchInput { click() }

            onNode(hasSetTextAction()).assertIsNotFocused()
        }

    @Test
    fun `GIVEN the composer focused in a new chat WHEN the greeting is tapped THEN the keyboard goes away`() =
        runComposeUiTest {
            setContent(conversation(stateNamed("New chat")))

            onNode(hasSetTextAction()).performClick()
            onNode(hasSetTextAction()).assertIsFocused()
            onNodeWithText("Evening, Emma").performTouchInput { click() }

            onNode(hasSetTextAction()).assertIsNotFocused()
        }

    /** Dragging the transcript to read back is the other way Messages hides the keyboard. */
    @Test
    fun `GIVEN the composer focused WHEN the transcript is dragged THEN the keyboard goes away`() =
        runComposeUiTest {
            setContent(conversation(stateNamed("A long answer arriving")))

            onNode(hasSetTextAction()).performClick()
            onNode(hasSetTextAction()).assertIsFocused()
            onNodeWithTag(CONVERSATION_TRANSCRIPT_TAG).performTouchInput { swipeDown() }

            onNode(hasSetTextAction()).assertIsNotFocused()
        }

    /** The screen following an answer as it arrives is not you scrolling: typing carries on. */
    @Test
    fun `GIVEN the composer focused WHEN an answer arriving scrolls the transcript THEN the keyboard stays`() =
        runComposeUiTest {
            setContent(conversation(stateNamed("A long answer arriving")))

            onNode(hasSetTextAction()).performClick()
            waitForIdle()

            onNode(hasSetTextAction()).assertIsFocused()
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
            onNodeWithText(getString(Res.string.conversation_thinking)).assertIsDisplayed()
        }

    @Test
    fun the_first_word_takes_the_dots_place() =
        runComposeUiTest {
            setContent(conversation(stateNamed("Answering")))

            // Words are their own sign that something is happening; two at once is noise.
            onNodeWithTag(THINKING_DOTS_TAG).assertDoesNotExist()
            onNodeWithText("Three things, in order of how much", substring = true).assertIsDisplayed()
        }

    // ---- a turn you can watch ----------------------------------------------

    @Test
    fun the_newest_question_says_it_was_sent() =
        runComposeUiTest {
            setContent(conversation(stateNamed("A finished exchange")))

            onNodeWithText(getString(Res.string.conversation_sent)).assertIsDisplayed()
        }

    /**
     * The check beside "Sent", seen by day and at night.
     *
     * The canvas drew it and the first build left it out, which on a dark phone read as a glyph
     * that had vanished. Measured from the rendered pixels rather than read off the code: 3:1 is
     * the floor for a graphic that carries meaning.
     */
    @Test
    fun the_sent_check_can_be_seen_by_day_and_at_night() {
        listOf(false, true).forEach { night ->
            runComposeUiTest {
                setContent {
                    StillTheme(darkTheme = night) {
                        ConversationContent(
                            state = stateNamed("A finished exchange"),
                            onComposerTextChange = {},
                            onSend = {},
                            onRetry = {},
                            onTryAgain = {},
                            onBack = {},
                        )
                    }
                }

                val contrast = contrastWithin(onNodeWithTag(SENT_GLYPH_TAG).captureToImage().toPixelMap())

                val theme = if (night) "at night" else "by day"
                assertTrue(contrast >= 3.0, "the check reads at only $contrast:1 $theme")
            }
        }
    }

    /** The contrast between the lightest and darkest pixels of an image: ink against its ground. */
    private fun contrastWithin(pixels: PixelMap): Double {
        var darkest = 1f
        var lightest = 0f
        for (x in 0 until pixels.width) {
            for (y in 0 until pixels.height) {
                val luminance = pixels[x, y].luminance()
                darkest = minOf(darkest, luminance)
                lightest = maxOf(lightest, luminance)
            }
        }
        return (lightest + 0.05) / (darkest + 0.05)
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

    /**
     * A model's thoughts stream far faster than anyone reads, so showing them was a blur of
     * letters. The screen says it is thinking, and nothing more.
     */
    @Test
    fun a_model_thinking_shows_only_the_status() =
        runComposeUiTest {
            setContent(conversation(stateNamed("Thinking out loud")))

            onNodeWithTag(THINKING_DOTS_TAG).assertIsDisplayed()
            onNodeWithText(getString(Res.string.conversation_thinking)).assertIsDisplayed()
            onNodeWithText("the user is asking", substring = true).assertDoesNotExist()
            onNodeWithText("list the events in order", substring = true).assertDoesNotExist()
        }

    @Test
    fun a_tool_is_named_in_words_while_it_runs() =
        runComposeUiTest {
            setContent(conversation(stateNamed("Using a tool")))

            onNodeWithText(getString(Res.string.tool_calendar_read_running)).assertIsDisplayed()
            onNodeWithText(getString(Res.string.conversation_thought, 3)).assertIsDisplayed()
            onNodeWithText("calendar_read", substring = true).assertDoesNotExist()
        }

    /** Issue #33: the tool line used to sit above the whole answer. */
    @Test
    fun `GIVEN an answer being written with text then a tool then text WHEN drawn THEN the tool line sits between the two stretches`() =
        runComposeUiTest {
            setContent(conversation(stateNamed("The answer arrives, a tool between its words")))

            val thought = onNodeWithText(getString(Res.string.conversation_thought, 4)).getUnclippedBoundsInRoot()
            val before = onNodeWithText("Let me check your calendar.").getUnclippedBoundsInRoot()
            val tool = onNodeWithText(getString(Res.string.tool_calendar_read_done)).getUnclippedBoundsInRoot()
            val after = onNodeWithText("Tomorrow's fairly light", substring = true).getUnclippedBoundsInRoot()
            assertTrue(thought.top < before.top, "the thinking came first")
            assertTrue(before.bottom <= tool.top, "the tool ran after the first words")
            assertTrue(tool.bottom <= after.top, "and before the rest")
        }

    /** #40: a search says what it looked for, a read names its page, a blocked read says why. */
    @Test
    fun `GIVEN an answer being researched WHEN drawn THEN each step says what it found or why not`() =
        runComposeUiTest {
            setContent(conversation(stateNamed("Researching: a search, a page read and one blocked")))

            onNodeWithText("Searched the web for “Hong Kong press freedom” · 2 results").assertIsDisplayed()
            onNodeWithText("Read Hong Kong: press freedom index").assertIsDisplayed()
            onNodeWithText(" · rsf.org", useUnmergedTree = true).assertIsDisplayed()
            onNodeWithText("Couldn’t read scmp.com · it blocks automated reading").assertIsDisplayed()
            onNodeWithText(getString(Res.string.tool_read_page_done)).assertDoesNotExist()
        }

    /** #40: a run of steps is labelled with what it did; a single step is not folded at all. */
    @Test
    fun `GIVEN a finished answer with steps between its text WHEN drawn THEN each run shows where it happened and says what it did`() =
        runComposeUiTest {
            setContent(conversation(stateNamed("A finished answer, its steps folded")))
            val checked = getString(Res.string.tool_calendar_read_done)

            val first = onNodeWithText(getString(Res.string.conversation_thought, 4)).getUnclippedBoundsInRoot()
            val before = onNodeWithText("Let me check your calendar.").getUnclippedBoundsInRoot()
            val second = onNodeWithText(checked).getUnclippedBoundsInRoot()
            val after = onNodeWithText("Tomorrow's fairly light. Nothing in the evening.").getUnclippedBoundsInRoot()
            assertTrue(first.bottom <= before.top, "a single step shows as it is")
            assertTrue(before.bottom <= second.top)
            assertTrue(second.bottom <= after.top)
            onNodeWithText(getString(Res.string.conversation_thought, 2)).assertDoesNotExist()
        }

    @Test
    fun `GIVEN a folded run of steps WHEN tapped THEN its steps show in place and tapping again hides them`() =
        runComposeUiTest {
            setContent(conversation(stateNamed("A finished answer, its steps folded")))
            val checked = getString(Res.string.tool_calendar_read_done)

            onNodeWithText(checked).performClick()

            onAllNodesWithText(checked).assertCountEquals(2)
            val thought = onNodeWithText(getString(Res.string.conversation_thought, 2)).getUnclippedBoundsInRoot()
            val before = onNodeWithText("Let me check your calendar.").getUnclippedBoundsInRoot()
            val after = onNodeWithText("Tomorrow's fairly light. Nothing in the evening.").getUnclippedBoundsInRoot()
            assertTrue(
                before.bottom <= thought.top && thought.bottom <= after.top,
                "the steps open where they happened",
            )

            onAllNodesWithText(checked)[0].performClick()

            onAllNodesWithText(checked).assertCountEquals(1)
            onNodeWithText(getString(Res.string.conversation_thought, 2)).assertDoesNotExist()
        }

    @Test
    fun `GIVEN a finished research answer WHEN drawn THEN its fold says what the steps did and what failed`() =
        runComposeUiTest {
            setContent(conversation(stateNamed("A researched answer, its steps folded")))

            val fold = onNodeWithText("Searched the web, read 2 pages · 1 failed")
            fold.assertIsDisplayed()
            // How many steps it holds is still told to a screen reader.
            assertEquals("Show 5 steps", fold.fetchSemanticsNode().config[SemanticsActions.OnClick].label)
            onNodeWithText("Read Hong Kong: press freedom index").assertDoesNotExist()
        }

    @Test
    fun `GIVEN an answer saved before parts were kept WHEN drawn THEN its text shows as it always did`() =
        runComposeUiTest {
            setContent(conversation(stateNamed("A finished exchange")))

            onNodeWithText("The panel review is tomorrow at 14:30.").assertIsDisplayed()
        }

    /** The chip went when the tool finished; without the dots the answer looked finished too. */
    @Test
    fun `GIVEN a tool that finished and no new words yet WHEN drawn THEN the dots show under the tool line`() =
        runComposeUiTest {
            setContent(conversation(stateNamed("Between a tool and the next words")))

            val tool = onNodeWithText(getString(Res.string.tool_calendar_read_done)).getUnclippedBoundsInRoot()
            val dots = onNodeWithTag(THINKING_DOTS_TAG).assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue(tool.bottom <= dots.top, "the dots sit where the next words will go")
        }

    /** Close under the steps, the dots read as one more step rather than as the turn still going. */
    @Test
    fun `GIVEN a tool that finished and no new words yet WHEN drawn THEN the dots stand at least 16dp clear of the tool line`() =
        runComposeUiTest {
            setContent(conversation(stateNamed("Between a tool and the next words")))

            val tool = onNodeWithText(getString(Res.string.tool_calendar_read_done)).getUnclippedBoundsInRoot()
            val dots = onNodeWithTag(THINKING_DOTS_TAG).getUnclippedBoundsInRoot()
            assertTrue(dots.top - tool.bottom >= 16.dp, "gap was ${dots.top - tool.bottom}")
        }

    @Test
    fun `GIVEN the model thinking after its first words WHEN drawn THEN the dots show`() =
        runComposeUiTest {
            setContent(conversation(stateNamed("Thinking mid-answer")))

            onNodeWithTag(THINKING_DOTS_TAG).assertIsDisplayed()
        }

    @Test
    fun `GIVEN words arriving WHEN drawn THEN there are no dots`() =
        runComposeUiTest {
            setContent(conversation(stateNamed("Answering")))

            onNodeWithTag(THINKING_DOTS_TAG).assertDoesNotExist()
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

    // ---- a question that never landed ----------------------------------------

    @Test
    fun `GIVEN a question that never landed WHEN it is shown THEN it carries the error glyph and a retry glyph`() =
        runComposeUiTest {
            setContent(conversation(stateNamed("Never reached the hub")))

            onNodeWithTag(NOT_SENT_GLYPH_TAG, useUnmergedTree = true).assertExists()
            onNodeWithTag(RETRY_GLYPH_TAG, useUnmergedTree = true).assertExists()
        }

    @Test
    fun `GIVEN a question that never landed WHEN it is shown THEN Retry sits on the same line after Not sent`() =
        runComposeUiTest {
            setContent(conversation(stateNamed("Never reached the hub")))

            val status = onNodeWithText(getString(Res.string.conversation_not_sent)).getUnclippedBoundsInRoot()
            val retry =
                onNodeWithText(getString(Res.string.conversation_retry), useUnmergedTree = true)
                    .getUnclippedBoundsInRoot()
            val statusMiddle = (status.top + status.bottom) / 2
            val retryMiddle = (retry.top + retry.bottom) / 2
            assertTrue((statusMiddle - retryMiddle).value in -1f..1f, "one line, not stacked")
            assertTrue(retry.left > status.right, "Retry follows the status")
        }

    @Test
    fun `GIVEN a question that never landed WHEN it is shown THEN Retry is still a full touch target`() =
        runComposeUiTest {
            setContent(conversation(stateNamed("Never reached the hub")))

            // A quiet link to look at, but a thumb still needs the whole 48.
            val retry = onNodeWithText(getString(Res.string.conversation_retry)).getUnclippedBoundsInRoot()
            assertTrue(retry.bottom - retry.top >= 48.dp, "was ${retry.bottom - retry.top}")
        }

    // ---- how a waiting status is laid out -------------------------------------

    @Test
    fun `GIVEN a model thinking WHEN the dots show THEN the word sits under them`() =
        runComposeUiTest {
            setContent(conversation(stateNamed("Thinking, before the first word")))

            val dots = onNodeWithTag(THINKING_DOTS_TAG).getUnclippedBoundsInRoot()
            val word =
                onNodeWithText(getString(Res.string.conversation_thinking), useUnmergedTree = true)
                    .getUnclippedBoundsInRoot()
            assertEquals(dots.left, word.left, "the word starts where the dots do")
            assertTrue(word.top > dots.top, "the word is below the dots, not beside them")
        }

    @Test
    fun `GIVEN a dropped stream WHEN it reconnects THEN the detail sits under the label`() =
        runComposeUiTest {
            setContent(conversation(stateNamed("Stream dropped, reconnecting")))

            assertStacked(
                label = getString(Res.string.conversation_reconnecting),
                detail = getString(Res.string.conversation_reconnecting_detail),
            )
        }

    @Test
    fun `GIVEN a turn past a minute WHEN it is still working THEN the detail sits under the label`() =
        runComposeUiTest {
            setContent(conversation(stateNamed("Still working after a minute")))

            assertStacked(
                label = getString(Res.string.conversation_still_working),
                detail = getString(Res.string.conversation_still_working_detail),
            )
        }

    private fun ComposeUiTest.assertStacked(
        label: String,
        detail: String,
    ) {
        val top = onNodeWithText(label).getUnclippedBoundsInRoot()
        val under = onNodeWithText(detail).getUnclippedBoundsInRoot()
        assertEquals(top.left, under.left, "label and detail share a left edge")
        assertTrue(under.top >= top.bottom, "the detail is on its own row, under the label")
    }

    // ---- choosing the agent of a new chat ----------------------------------

    @Test
    fun `GIVEN a new chat WHEN the avatar is tapped THEN the sheet offers every agent`() =
        runComposeUiTest {
            setContent(conversation(stateNamed("New chat")))

            onNodeWithContentDescription(getString(Res.string.conversation_change_agent)).performClick()

            onNodeWithText(getString(Res.string.agent_picker_title)).assertIsDisplayed()
            onNodeWithText("Academic Researcher").assertIsDisplayed()
            onNodeWithText("Hardware Scout").assertIsDisplayed()
            onNodeWithText("Liam’s").assertIsDisplayed()
            // Every card says what its agent may do, in words: all three here can search the web.
            onAllNodesWithText("Search the web", useUnmergedTree = true).assertCountEquals(3)
        }

    @Test
    fun `GIVEN a chat that has started WHEN it is shown THEN there is no way to change its agent`() =
        runComposeUiTest {
            setContent(conversation(stateNamed("A finished exchange")))

            onAllNodes(hasContentDescription(getString(Res.string.conversation_change_agent))).assertCountEquals(0)
        }

    @Test
    fun `GIVEN the sheet is open WHEN another agent is picked THEN it is chosen and the sheet closes`() =
        runComposeUiTest {
            var picked: String? = null
            setContent(conversation(stateNamed("New chat"), onSelectAgent = { picked = it }))

            onNodeWithContentDescription(getString(Res.string.conversation_change_agent)).performClick()
            onNodeWithText("Academic Researcher").performClick()
            waitForIdle()

            assertEquals("agent-research", picked)
            onNodeWithText(getString(Res.string.agent_picker_title)).assertDoesNotExist()
        }

    @Test
    fun `GIVEN the agents did not load WHEN the sheet opens THEN it says so and can ask again`() =
        runComposeUiTest {
            var retried = false
            setContent(conversation(stateNamed("New chat, agents didn't load"), onRetryAgents = { retried = true }))

            onNodeWithContentDescription(getString(Res.string.conversation_change_agent)).performClick()

            onNodeWithText(getString(Res.string.agent_picker_failed_title)).assertIsDisplayed()
            onNodeWithText(getString(Res.string.conversation_try_again)).performClick()
            assertTrue(retried)
        }

    /** Canvas: ToolApproveRemove. The removal reads in red and "Remove" is the answer on the right. */
    @Test
    fun `GIVEN a removal waiting on the member WHEN drawn THEN the card says what goes and when and asks Keep it or Remove`() =
        runComposeUiTest {
            val decided = mutableListOf<Pair<String, Boolean>>()
            setContent(conversation(stateNamed("Approving a removal"), onDecide = { id, ok, _ -> decided += id to ok }))

            onNodeWithText(getString(Res.string.tool_calendar_remove_card)).assertIsDisplayed()
            onNodeWithText("Print shop").assertIsDisplayed()
            onNodeWithText("Fri 11 Sep, 18:00").assertIsDisplayed()
            val keep = onNodeWithText(getString(Res.string.tool_card_keep)).getUnclippedBoundsInRoot()
            val remove = onNodeWithText(getString(Res.string.tool_card_remove)).getUnclippedBoundsInRoot()
            assertTrue(keep.right <= remove.left, "the main action is on the right")

            onNodeWithText(getString(Res.string.tool_card_remove)).performClick()
            onNodeWithText(getString(Res.string.tool_card_keep)).performClick()

            assertEquals(listOf("c-1" to true, "c-1" to false), decided)
        }

    @Test
    fun `GIVEN one write approved and one declined WHEN the answer is drawn THEN the fold names both and opens to their record lines`() =
        runComposeUiTest {
            setContent(conversation(stateNamed("Approved and declined")))

            val fold = onNodeWithText("Added Dinner together, didn’t add Buy flowers")
            fold.assertIsDisplayed()
            fold.performClick()

            onNodeWithText("Added to your calendar · Dinner together").assertIsDisplayed()
            onNodeWithText("${getString(Res.string.tool_calendar_add_declined)} · Buy flowers").assertIsDisplayed()
        }

    @Test
    fun `GIVEN Send pressed while a card waits WHEN drawn THEN the words stay and the hold line asks for the card first`() =
        runComposeUiTest {
            setContent(conversation(stateNamed("Waiting on a card, Send held")))

            onNodeWithText(getString(Res.string.tool_card_hold)).assertIsDisplayed()
            onNode(hasSetTextAction()).assertTextContains("Also lunch on Sunday?")
            onNodeWithText("Dinner together").assertIsDisplayed()
        }

    /** Canvas: ToolFailed. What broke, that nothing was added, and the button that fixes it. */
    @Test
    fun `GIVEN an add the calendar refused WHEN the answer is drawn THEN the card says so and Reconnect calendar opens the connect flow`() =
        runComposeUiTest {
            var connects = 0
            setContent(conversation(stateNamed("A tool that failed"), onConnectCalendar = { connects++ }))

            onNodeWithText(getString(Res.string.tool_fix_calendar_rejected_title)).assertIsDisplayed()
            val caption =
                "${getString(
                    Res.string.tool_fix_calendar_rejected_detail,
                )} ${getString(Res.string.tool_fix_nothing_added)}"
            onNodeWithText(caption).assertIsDisplayed()
            // The card says what broke; the red step isn't repeated above it.
            onAllNodesWithText(getString(Res.string.tool_calendar_add_failed)).assertCountEquals(0)

            onNodeWithText(getString(Res.string.tool_fix_reconnect_calendar)).performClick()

            assertEquals(1, connects)
        }

    /** Canvas: ToolFixedCard. The retry icon asks the answer's question again, as a new message. */
    @Test
    fun `GIVEN a fixed step in the latest answer WHEN the card is drawn THEN it says the calendar is connected and Ask again sends the question`() =
        runComposeUiTest {
            val sent = mutableListOf<String>()
            setContent(conversation(stateNamed("A fixed tool, the latest answer"), onSend = { sent += it }))

            onNodeWithText(getString(Res.string.tool_fix_calendar_fixed_title)).assertIsDisplayed()
            onAllNodesWithText(getString(Res.string.tool_fix_reconnect_calendar)).assertCountEquals(0)

            onNodeWithContentDescription(getString(Res.string.tool_fix_ask_again)).performClick()

            assertEquals(listOf("Put the print shop cutoff in my calendar"), sent)
        }

    /** Canvas: ToolFixedLater. */
    @Test
    fun `GIVEN a fixed step and a newer message after it WHEN the card is drawn THEN there is no Ask again`() =
        runComposeUiTest {
            setContent(conversation(stateNamed("A fixed tool, a newer message after it")))

            onNodeWithText(getString(Res.string.tool_fix_calendar_fixed_title)).assertIsDisplayed()
            onAllNodes(hasContentDescription(getString(Res.string.tool_fix_ask_again))).assertCountEquals(0)
        }

    @Test
    fun `GIVEN an add the calendar refused after other steps WHEN the answer is drawn THEN the red step stays in the fold and the card stays out`() =
        runComposeUiTest {
            setContent(conversation(stateNamed("A tool that failed, among other steps")))

            onNodeWithText(getString(Res.string.tool_fix_reconnect_calendar)).assertIsDisplayed()
            val fold = onNodeWithText(getString(Res.string.tool_calendar_add_failed))
            fold.assertIsDisplayed()
            fold.performClick()

            // Opened, the fold shows the red step under its label; the card is still there.
            onAllNodesWithText(getString(Res.string.tool_calendar_add_failed)).assertCountEquals(2)
            onNodeWithText(getString(Res.string.tool_fix_reconnect_calendar)).assertIsDisplayed()
        }

    // ---- auto-approve from the card (slice 4, PR 5) -------------------------

    /** Canvas: ToolAutoApprove. Ticking the box changes nothing until Approve. */
    @Test
    fun `GIVEN an add waiting on the member WHEN the box is ticked and it is approved THEN it is approved automatically`() =
        runComposeUiTest {
            val decided = mutableListOf<Pair<String, Boolean>>()
            val automatic = mutableListOf<String>()
            setContent(
                conversation(
                    stateNamed("Auto-approving an add"),
                    onDecide = { id, ok, _ -> decided += id to ok },
                    onApproveAutomatically = { automatic += it },
                ),
            )
            val box = onNodeWithText(getString(Res.string.tool_calendar_add_automatic_ask))
            box.assertIsOff()

            box.performClick()
            box.assertIsOn()
            onNodeWithText(getString(Res.string.tool_card_approve)).performClick()

            assertEquals(listOf("c-1"), automatic)
            assertEquals(emptyList(), decided)
        }

    @Test
    fun `GIVEN an add waiting on the member WHEN approved without ticking THEN it is only approved`() =
        runComposeUiTest {
            val decided = mutableListOf<Pair<String, Boolean>>()
            val automatic = mutableListOf<String>()
            setContent(
                conversation(
                    stateNamed("Auto-approving an add"),
                    onDecide = { id, ok, _ -> decided += id to ok },
                    onApproveAutomatically = { automatic += it },
                ),
            )

            onNodeWithText(getString(Res.string.tool_card_approve)).performClick()

            assertEquals(listOf("c-1" to true), decided)
            assertEquals(emptyList(), automatic)
        }

    @Test
    fun `GIVEN a removal waiting on the member WHEN drawn THEN it never offers to make removing automatic`() =
        runComposeUiTest {
            setContent(conversation(stateNamed("Approving a removal")))

            onNodeWithText(getString(Res.string.tool_calendar_remove_card)).assertIsDisplayed()
            onAllNodes(hasText("Auto-approve", substring = true)).assertCountEquals(0)
        }

    /** Canvas: ToolAutoApproved. */
    @Test
    fun `GIVEN an add made automatic WHEN drawn THEN its record says automatic and Undo asks again`() =
        runComposeUiTest {
            var undone = false
            setContent(conversation(stateNamed("Adding is automatic"), onUndoAutomatic = { undone = true }))

            onNodeWithText("Added to your calendar · Print shop cutoff · automatic").assertIsDisplayed()
            onNodeWithText(getString(Res.string.tool_calendar_add_now_automatic)).assertIsDisplayed()
            onNodeWithText(getString(Res.string.tool_automatic_undo)).performClick()

            assertTrue(undone)
        }

    // ---- edit before approving (slice 4, PR 4) -------------------------------

    private fun editing(decided: MutableList<Triple<String, Boolean, ProposalDetails?>>): @Composable () -> Unit =
        conversation(
            stateNamed("Auto-approving an add"),
            onDecide = { id, ok, edit -> decided += Triple(id, ok, edit) },
        )

    private fun sent(start: EventMoment) =
        listOf(
            Triple<String, Boolean, ProposalDetails?>(
                "c-1",
                true,
                ProposalDetails.CalendarEvent(title = null, start = start, end = null, allDay = false),
            ),
        )

    /** Canvas: ToolEdit. The title is typed; the day and the time are picked, never typed. */
    @Test
    fun `GIVEN an add waiting on the member WHEN Edit is tapped THEN the title is a field and the day and time open pickers`() =
        runComposeUiTest {
            setContent(editing(mutableListOf()))

            val decline = onNodeWithText(getString(Res.string.tool_card_decline)).getUnclippedBoundsInRoot()
            val edit = onNodeWithText(getString(Res.string.tool_card_edit)).getUnclippedBoundsInRoot()
            val approve = onNodeWithText(getString(Res.string.tool_card_approve)).getUnclippedBoundsInRoot()
            assertTrue(decline.right <= edit.left && edit.right <= approve.left, "Decline, Edit, then Approve")

            onNodeWithText(getString(Res.string.tool_card_edit)).performClick()

            onNode(hasSetTextAction() and hasText("Dinner together")).assertIsDisplayed()
            onNode(hasSetTextAction() and hasText("Sat 3 Oct")).assertDoesNotExist()
            onNode(hasSetTextAction() and hasText("20:00")).assertDoesNotExist()
            onNodeWithTag(DAY_FIELD_TAG).assertTextContains("Sat 3 Oct").performClick()
            onNodeWithTag(PICKER_CONFIRM_TAG).assertIsDisplayed()
            onNodeWithTag(PICKER_DISMISS_TAG).performClick()
            onNodeWithTag(TIME_FIELD_TAG).assertTextContains("20:00").performClick()
            onNodeWithTag(PICKER_CONFIRM_TAG).assertIsDisplayed()
        }

    @Test
    fun `GIVEN a card being edited WHEN a time is picked THEN it is marked and Approve with changes sends it`() =
        runComposeUiTest {
            val decided = mutableListOf<Triple<String, Boolean, ProposalDetails?>>()
            setContent(editing(decided))
            onNodeWithText(getString(Res.string.tool_card_edit)).performClick()

            onNodeWithTag(TIME_FIELD_TAG).performClick()
            onNodeWithContentDescription("21 hours").performClick()
            // A tap on the dial moves on to minutes by itself; a test's click doesn't, so it asks.
            onNode(hasContentDescription("Select minutes", substring = true)).performClick()
            onNodeWithContentDescription("30 minutes").performClick()
            onNodeWithTag(PICKER_CONFIRM_TAG).performClick()

            onNodeWithTag(TIME_FIELD_TAG).assertTextContains("21:30")
            onNodeWithText(
                getString(Res.string.field_changed, getString(Res.string.tool_card_field_time)),
            ).assertIsDisplayed()
            onNodeWithText(getString(Res.string.tool_card_approve_with_changes)).performClick()
            assertEquals(sent(EventMoment(2026, 10, 3, hour = 21, minute = 30)), decided)
        }

    @Test
    fun `GIVEN a card being edited WHEN a day is picked THEN it is marked and Approve with changes sends it`() =
        runComposeUiTest {
            val decided = mutableListOf<Triple<String, Boolean, ProposalDetails?>>()
            setContent(editing(decided))
            onNodeWithText(getString(Res.string.tool_card_edit)).performClick()

            onNodeWithTag(DAY_FIELD_TAG).performClick()
            // The picker words its days in the machine's locale: "Sunday, 4 October 2026" or "Sunday, October 4, 2026".
            val fourth = hasText("4 October 2026", substring = true) or hasText("October 4, 2026", substring = true)
            onNode(hasText("Sunday", substring = true) and fourth).performClick()
            onNodeWithTag(PICKER_CONFIRM_TAG).performClick()

            onNodeWithTag(DAY_FIELD_TAG).assertTextContains("Sun 4 Oct")
            onNodeWithText(
                getString(Res.string.field_changed, getString(Res.string.tool_card_field_day)),
            ).assertIsDisplayed()
            onNodeWithText(getString(Res.string.tool_card_approve_with_changes)).performClick()
            assertEquals(sent(EventMoment(2026, 10, 4, hour = 20, minute = 0)), decided)
        }

    @Test
    fun `GIVEN a picker open WHEN it is dismissed THEN the field stays as proposed`() =
        runComposeUiTest {
            setContent(editing(mutableListOf()))
            onNodeWithText(getString(Res.string.tool_card_edit)).performClick()

            onNodeWithTag(TIME_FIELD_TAG).performClick()
            onNodeWithContentDescription("21 hours").performClick()
            onNodeWithTag(PICKER_DISMISS_TAG).performClick()

            onNodeWithTag(TIME_FIELD_TAG).assertTextContains("20:00")
            onNodeWithText(getString(Res.string.tool_card_approve_with_changes)).assertDoesNotExist()
        }

    @Test
    fun `GIVEN a card being edited WHEN the title is emptied THEN Approve says why and sends nothing`() =
        runComposeUiTest {
            val decided = mutableListOf<Triple<String, Boolean, ProposalDetails?>>()
            setContent(editing(decided))
            onNodeWithText(getString(Res.string.tool_card_edit)).performClick()

            onNode(hasSetTextAction() and hasText("Dinner together")).performTextReplacement("")
            onNodeWithText(getString(Res.string.tool_card_field_empty)).assertDoesNotExist()
            onNodeWithText(getString(Res.string.tool_card_approve_with_changes)).performClick()

            onNodeWithText(getString(Res.string.tool_card_field_empty)).assertIsDisplayed()
            assertEquals(emptyList(), decided)
        }

    @Test
    fun `GIVEN a card being edited WHEN Cancel is tapped THEN the edits go and plain Approve sends no edit`() =
        runComposeUiTest {
            val decided = mutableListOf<Triple<String, Boolean, ProposalDetails?>>()
            setContent(editing(decided))

            onNodeWithText(getString(Res.string.tool_card_edit)).performClick()
            onNode(hasSetTextAction() and hasText("Dinner together")).performTextReplacement("Dinner out")
            onNodeWithText(getString(Res.string.tool_card_cancel)).performClick()

            onNodeWithText("Dinner together").assertIsDisplayed()
            onNodeWithText("Sat 3 Oct, 20:00").assertIsDisplayed()
            onNodeWithText(getString(Res.string.tool_card_approve)).performClick()

            assertEquals(listOf(Triple<String, Boolean, ProposalDetails?>("c-1", true, null)), decided)
        }

    @Test
    fun `GIVEN a removal waiting on the member WHEN drawn THEN it offers no Edit`() =
        runComposeUiTest {
            setContent(conversation(stateNamed("Approving a removal")))

            onNodeWithText(getString(Res.string.tool_card_edit)).assertDoesNotExist()
        }
}
