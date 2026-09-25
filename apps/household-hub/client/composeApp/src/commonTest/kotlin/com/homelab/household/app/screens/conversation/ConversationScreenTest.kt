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
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import com.homelab.household.app.components.NOT_SENT_GLYPH_TAG
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
import com.homelab.household.app.resources.send_message
import com.homelab.household.app.resources.tool_calendar_read_done
import com.homelab.household.app.resources.tool_calendar_read_failed
import com.homelab.household.app.resources.tool_calendar_read_running
import com.homelab.household.app.resources.tool_read_page_done
import com.homelab.household.app.testing.StillTheme
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
            onNodeWithText("Read Hong Kong: press freedom index · rsf.org").assertIsDisplayed()
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
            onNodeWithText("Read Hong Kong: press freedom index · rsf.org").assertDoesNotExist()
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
}
