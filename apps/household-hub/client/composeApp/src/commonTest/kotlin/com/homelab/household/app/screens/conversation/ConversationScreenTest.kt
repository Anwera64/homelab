package com.homelab.household.app.screens.conversation

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.homelab.household.domain.model.EventMoment
import com.homelab.household.domain.model.EventRepeat
import com.homelab.household.domain.model.EventScope
import com.homelab.household.domain.model.ProposalDetails
import com.homelab.household.domain.model.RepeatChange
import com.homelab.household.domain.model.RepeatEvery
import com.homelab.household.presentation.chatsession.ChatSessionUiState
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

    @Test
    fun a_new_chat_greets_you_instead_of_showing_an_empty_transcript() =
        runComposeUiTest {
            conversationScreen(stateNamed("New chat"))

            onConversation {
                seesGreeting("Emma")
                seesTagline("Schedules, meals, keeping the week straight.")
                seesAgent("Home Coordinator")
            }
        }

    @Test
    fun `GIVEN a chat that was looked over WHEN drawn THEN the line sits after the last message read and a tap opens what it remembered`() =
        runComposeUiTest {
            var opened = 0
            conversationScreen(stateNamed("Looked over"), onLookedOver = { opened++ })

            onConversation {
                seesTheLookedOverLine(remembered = 2, toCheck = 1, time = "10:52")
                val line = boundsOfTheLookedOverLine()
                assertTrue(boundsOfWords("The panel review is tomorrow at 14:30.").bottom <= line.top)
                assertTrue(line.bottom <= boundsOfWords("And what about Friday?").top)
                assertTrue(line.bottom - line.top >= 48.dp)
                tapsTheLookedOverLine()
            }

            assertEquals(1, opened)
        }

    @Test
    fun `GIVEN a look-over that kept nothing WHEN drawn THEN the line says nothing new and opens nothing`() =
        runComposeUiTest {
            var opened = 0
            conversationScreen(stateNamed("Looked over, nothing new"), onLookedOver = { opened++ })

            onConversation {
                seesNothingNewAt("10:52")
                tapsTheLookedOverLine()
            }

            assertEquals(0, opened)
        }

    @Test
    fun a_question_that_never_arrived_is_marked_on_your_own_bubble_and_offers_to_resend() =
        runComposeUiTest {
            val resent = mutableListOf<String>()
            conversationScreen(stateNamed("Never reached the hub"), onRetry = { resent += it })

            onConversation {
                seesNotSent()
                tapsRetry()
            }

            assertEquals(listOf("Actually, make it 20:30"), resent)
        }

    @Test
    fun a_dropped_stream_keeps_the_half_answer_and_says_it_is_reconnecting() =
        runComposeUiTest {
            conversationScreen(stateNamed("Stream dropped, reconnecting"))

            onConversation {
                // The words that had arrived stay put; the indicator sits where they stopped.
                seesWords("so if the boards aren't", partly = true)
                seesReconnecting()
                seesComposerWaiting()
            }
        }

    @Test
    fun the_question_is_not_blamed_when_the_stream_drops() =
        runComposeUiTest {
            conversationScreen(stateNamed("Stream dropped, reconnecting"))

            onConversation {
                // It arrived. Saying otherwise is what sent people into duplicate sends.
                doesNotSeeNotSent()
                doesNotSeeRetry()
            }
        }

    @Test
    fun past_the_active_wait_the_composer_says_you_can_leave() =
        runComposeUiTest {
            conversationScreen(stateNamed("Still working after a minute"))

            onConversation {
                seesStillWorking()
                seesComposerSaysYouCanLeave()
                // Not a failure: nothing here offers to do it again.
                doesNotSeeTryAgain()
            }
        }

    @Test
    fun an_answer_that_failed_offers_a_fresh_one_and_says_the_question_arrived() =
        runComposeUiTest {
            var triedAgain = false
            conversationScreen(stateNamed("The answer failed"), onTryAgain = { triedAgain = true })

            onConversation {
                seesAnswerFailed()
                seesTheQuestionArrived()
                tapsTryAgain()
            }

            assertEquals(true, triedAgain)
        }

    @Test
    fun the_composer_does_not_send_while_an_answer_is_being_written() =
        runComposeUiTest {
            val sent = mutableListOf<String>()
            conversationScreen(
                stateNamed("Stream dropped, reconnecting").copy(composerText = "and another thing"),
                onSend = { sent += it },
            )

            onConversation { tapsSend() }

            // A second send would answer 409; the placeholder says so instead of failing at it.
            assertEquals(emptyList(), sent)
        }

    @Test
    fun the_composer_sends_when_nothing_is_in_flight() =
        runComposeUiTest {
            val sent = mutableListOf<String>()
            conversationScreen(
                stateNamed("A finished exchange").copy(composerText = "And Saturday?"),
                onSend = { sent += it },
            )

            onConversation { tapsSend() }

            assertEquals(listOf("And Saturday?"), sent)
        }

    @Test
    fun the_keyboard_send_key_goes_through_the_same_guard_as_the_button() =
        runComposeUiTest {
            val sent = mutableListOf<String>()
            conversationScreen(
                stateNamed("Stream dropped, reconnecting").copy(composerText = "and another thing"),
                onSend = { sent += it },
            )

            onConversation { pressesSendOnTheKeyboard() }

            // Otherwise Enter would be a way round the rule the send button obeys.
            assertEquals(emptyList(), sent)
        }

    @Test
    fun the_keyboard_send_key_sends_when_nothing_is_in_flight() =
        runComposeUiTest {
            val sent = mutableListOf<String>()
            conversationScreen(
                stateNamed("A finished exchange").copy(composerText = "And Saturday?"),
                onSend = { sent += it },
            )

            onConversation { pressesSendOnTheKeyboard() }

            assertEquals(listOf("And Saturday?"), sent)
        }

    /**
     * iOS has no back button to hide the keyboard, and the composer's return key sends. Tapping the
     * conversation, away from the composer, is how Messages lets it go, so it must clear focus.
     */
    @Test
    fun `GIVEN the composer focused WHEN the transcript is tapped THEN the keyboard goes away`() =
        runComposeUiTest {
            conversationScreen(stateNamed("A finished exchange"))

            onConversation {
                tapsTheComposer()
                seesTheComposerFocused()
                tapsWordsAwayFromTheComposer("The panel review is tomorrow at 14:30.")

                seesTheComposerNotFocused()
            }
        }

    @Test
    fun `GIVEN the composer focused in a new chat WHEN the greeting is tapped THEN the keyboard goes away`() =
        runComposeUiTest {
            conversationScreen(stateNamed("New chat"))

            onConversation {
                tapsTheComposer()
                seesTheComposerFocused()
                tapsWordsAwayFromTheComposer("Evening, Emma")

                seesTheComposerNotFocused()
            }
        }

    /** Dragging the transcript to read back is the other way Messages hides the keyboard. */
    @Test
    fun `GIVEN the composer focused WHEN the transcript is dragged THEN the keyboard goes away`() =
        runComposeUiTest {
            conversationScreen(stateNamed("A long answer arriving"))

            onConversation {
                tapsTheComposer()
                seesTheComposerFocused()
                dragsTheTranscriptDown()

                seesTheComposerNotFocused()
            }
        }

    /** The screen following an answer as it arrives is not you scrolling: typing carries on. */
    @Test
    fun `GIVEN the composer focused WHEN an answer arriving scrolls the transcript THEN the keyboard stays`() =
        runComposeUiTest {
            conversationScreen(stateNamed("A long answer arriving"))

            onConversation { tapsTheComposer() }
            waitForIdle()

            onConversation { seesTheComposerFocused() }
        }

    @Test
    fun a_send_that_failed_says_so_and_keeps_what_you_typed() =
        runComposeUiTest {
            conversationScreen(
                stateNamed("A finished exchange").copy(
                    composerText = "Hi",
                    errorMessage = "Can't reach your hub",
                ),
            )

            onConversation {
                // Silence here is what made a failed send look like nothing happening at all.
                seesError("Can't reach your hub")
                seesComposerHolding("Hi")
            }
        }

    /**
     * The silence between sending and the first word, which is the longest in the app: a model
     * being loaded into memory, or one that thinks before it writes. It used to be an empty bubble
     * and nothing else, which reads as nothing having happened at all.
     */
    @Test
    fun before_the_first_word_arrives_the_screen_says_it_is_thinking() =
        runComposeUiTest {
            conversationScreen(stateNamed("Thinking, before the first word"))

            onConversation {
                seesThinkingDots()
                seesThinking()
            }
        }

    @Test
    fun the_first_word_takes_the_dots_place() =
        runComposeUiTest {
            conversationScreen(stateNamed("Answering"))

            onConversation {
                // Words are their own sign that something is happening; two at once is noise.
                doesNotSeeThinkingDots()
                seesWords("Three things, in order of how much", partly = true)
            }
        }

    // ---- a turn you can watch ----------------------------------------------

    @Test
    fun the_newest_question_says_it_was_sent() =
        runComposeUiTest {
            conversationScreen(stateNamed("A finished exchange"))

            onConversation { seesSent() }
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
                conversationScreen(stateNamed("A finished exchange"), darkTheme = night)

                onConversation {
                    val contrast = contrastOfTheSentCheck()

                    val theme = if (night) "at night" else "by day"
                    assertTrue(contrast >= 3.0, "the check reads at only $contrast:1 $theme")
                }
            }
        }
    }

    @Test
    fun a_question_that_never_landed_does_not_claim_to_have_been_sent() =
        runComposeUiTest {
            conversationScreen(stateNamed("Never reached the hub"))

            onConversation { doesNotSeeSent() }
        }

    @Test
    fun while_an_answer_is_being_written_the_composer_says_it_is_waiting() =
        runComposeUiTest {
            conversationScreen(stateNamed("Thinking, before the first word"))

            // It used to keep its idle words while refusing every send, which read as broken.
            onConversation { seesComposerWaiting() }
        }

    /**
     * A model's thoughts stream far faster than anyone reads, so showing them was a blur of
     * letters. The screen says it is thinking, and nothing more.
     */
    @Test
    fun a_model_thinking_shows_only_the_status() =
        runComposeUiTest {
            conversationScreen(stateNamed("Thinking out loud"))

            onConversation {
                seesThinkingDots()
                seesThinking()
                doesNotSeeWords("the user is asking", partly = true)
                doesNotSeeWords("list the events in order", partly = true)
            }
        }

    @Test
    fun a_tool_is_named_in_words_while_it_runs() =
        runComposeUiTest {
            conversationScreen(stateNamed("Using a tool"))

            onConversation {
                seesCheckingYourCalendar()
                seesThought(3)
                doesNotSeeWords("calendar_read", partly = true)
            }
        }

    /** Issue #33: the tool line used to sit above the whole answer. */
    @Test
    fun `GIVEN an answer being written with text then a tool then text WHEN drawn THEN the tool line sits between the two stretches`() =
        runComposeUiTest {
            conversationScreen(stateNamed("The answer arrives, a tool between its words"))

            onConversation {
                val thought = boundsOfThought(4)
                val before = boundsOfWords("Let me check your calendar.")
                val tool = boundsOfCalendarChecked()
                val after = boundsOfWords("Tomorrow's fairly light", partly = true)
                assertTrue(thought.top < before.top, "the thinking came first")
                assertTrue(before.bottom <= tool.top, "the tool ran after the first words")
                assertTrue(tool.bottom <= after.top, "and before the rest")
            }
        }

    /** #40: a search says what it looked for, a read names its page, a blocked read says why. */
    @Test
    fun `GIVEN an answer being researched WHEN drawn THEN each step says what it found or why not`() =
        runComposeUiTest {
            conversationScreen(stateNamed("Researching: a search, a page read and one blocked"))

            onConversation {
                seesSearched("Hong Kong press freedom", results = 2)
                seesRead("Hong Kong: press freedom index", host = "rsf.org")
                seesCouldNotReadBecauseItBlocks("scmp.com")
                doesNotSeeReadAPage()
            }
        }

    /** #40: a run of steps is labelled with what it did; a single step is not folded at all. */
    @Test
    fun `GIVEN a finished answer with steps between its text WHEN drawn THEN each run shows where it happened and says what it did`() =
        runComposeUiTest {
            conversationScreen(stateNamed("A finished answer, its steps folded"))

            onConversation {
                val first = boundsOfThought(4)
                val before = boundsOfWords("Let me check your calendar.")
                val second = boundsOfCalendarChecked()
                val after = boundsOfWords("Tomorrow's fairly light. Nothing in the evening.")
                assertTrue(first.bottom <= before.top, "a single step shows as it is")
                assertTrue(before.bottom <= second.top)
                assertTrue(second.bottom <= after.top)
                doesNotSeeThought(2)
            }
        }

    @Test
    fun `GIVEN a folded run of steps WHEN tapped THEN its steps show in place and tapping again hides them`() =
        runComposeUiTest {
            conversationScreen(stateNamed("A finished answer, its steps folded"))

            onConversation {
                tapsCalendarChecked()

                seesCalendarCheckedTimes(2)
                val thought = boundsOfThought(2)
                val before = boundsOfWords("Let me check your calendar.")
                val after = boundsOfWords("Tomorrow's fairly light. Nothing in the evening.")
                assertTrue(
                    before.bottom <= thought.top && thought.bottom <= after.top,
                    "the steps open where they happened",
                )

                tapsTheFirstCalendarChecked()

                seesCalendarCheckedTimes(1)
                doesNotSeeThought(2)
            }
        }

    @Test
    fun `GIVEN a finished research answer WHEN drawn THEN its fold says what the steps did and what failed`() =
        runComposeUiTest {
            conversationScreen(stateNamed("A researched answer, its steps folded"))

            onConversation {
                // How many steps it holds is still told to a screen reader.
                seesResearchFold(pagesRead = 2, failed = 1, steps = 5)
                doesNotSeeRead("Hong Kong: press freedom index", host = "rsf.org")
            }
        }

    @Test
    fun `GIVEN an answer saved before parts were kept WHEN drawn THEN its text shows as it always did`() =
        runComposeUiTest {
            conversationScreen(stateNamed("A finished exchange"))

            onConversation { seesWords("The panel review is tomorrow at 14:30.") }
        }

    /** The chip went when the tool finished; without the dots the answer looked finished too. */
    @Test
    fun `GIVEN a tool that finished and no new words yet WHEN drawn THEN the dots show under the tool line`() =
        runComposeUiTest {
            conversationScreen(stateNamed("Between a tool and the next words"))

            onConversation {
                val tool = boundsOfCalendarChecked()
                seesThinkingDots()
                val dots = boundsOfTheDots()
                assertTrue(tool.bottom <= dots.top, "the dots sit where the next words will go")
            }
        }

    /** Close under the steps, the dots read as one more step rather than as the turn still going. */
    @Test
    fun `GIVEN a tool that finished and no new words yet WHEN drawn THEN the dots stand at least 16dp clear of the tool line`() =
        runComposeUiTest {
            conversationScreen(stateNamed("Between a tool and the next words"))

            onConversation {
                val tool = boundsOfCalendarChecked()
                val dots = boundsOfTheDots()
                assertTrue(dots.top - tool.bottom >= 16.dp, "gap was ${dots.top - tool.bottom}")
            }
        }

    @Test
    fun `GIVEN the model thinking after its first words WHEN drawn THEN the dots show`() =
        runComposeUiTest {
            conversationScreen(stateNamed("Thinking mid-answer"))

            onConversation { seesThinkingDots() }
        }

    @Test
    fun `GIVEN words arriving WHEN drawn THEN there are no dots`() =
        runComposeUiTest {
            conversationScreen(stateNamed("Answering"))

            onConversation { doesNotSeeThinkingDots() }
        }

    @Test
    fun a_tool_that_could_not_run_says_so_in_the_trail() =
        runComposeUiTest {
            conversationScreen(stateNamed("A tool that couldn't run"))

            onConversation { seesCalendarCouldNotBeChecked() }
        }

    /** A failed turn has a card of its own to show; the dots would say it was still coming. */
    @Test
    fun an_answer_that_failed_shows_no_dots() =
        runComposeUiTest {
            conversationScreen(stateNamed("The answer failed"))

            onConversation { doesNotSeeThinkingDots() }
        }

    @Test
    fun every_previewed_state_draws() {
        provider.values.toList().forEach { state ->
            runComposeUiTest {
                conversationScreen(state)

                onConversation { seesAgent("Home Coordinator") }
            }
        }
    }

    // ---- a question that never landed ----------------------------------------

    @Test
    fun `GIVEN a question that never landed WHEN it is shown THEN it carries the error glyph and a retry glyph`() =
        runComposeUiTest {
            conversationScreen(stateNamed("Never reached the hub"))

            onConversation {
                seesTheNotSentGlyph()
                seesTheRetryGlyph()
            }
        }

    @Test
    fun `GIVEN a question that never landed WHEN it is shown THEN Retry sits on the same line after Not sent`() =
        runComposeUiTest {
            conversationScreen(stateNamed("Never reached the hub"))

            onConversation {
                val status = boundsOfNotSent()
                val retry = boundsOfRetryLink()
                val statusMiddle = (status.top + status.bottom) / 2
                val retryMiddle = (retry.top + retry.bottom) / 2
                assertTrue((statusMiddle - retryMiddle).value in -1f..1f, "one line, not stacked")
                assertTrue(retry.left > status.right, "Retry follows the status")
            }
        }

    @Test
    fun `GIVEN a question that never landed WHEN it is shown THEN Retry is still a full touch target`() =
        runComposeUiTest {
            conversationScreen(stateNamed("Never reached the hub"))

            onConversation {
                // A quiet link to look at, but a thumb still needs the whole 48.
                val retry = boundsOfRetryTarget()
                assertTrue(retry.bottom - retry.top >= 48.dp, "was ${retry.bottom - retry.top}")
            }
        }

    // ---- how a waiting status is laid out -------------------------------------

    @Test
    fun `GIVEN a model thinking WHEN the dots show THEN the word sits under them`() =
        runComposeUiTest {
            conversationScreen(stateNamed("Thinking, before the first word"))

            onConversation {
                val dots = boundsOfTheDots()
                val word = boundsOfThinking()
                assertEquals(dots.left, word.left, "the word starts where the dots do")
                assertTrue(word.top > dots.top, "the word is below the dots, not beside them")
            }
        }

    @Test
    fun `GIVEN a dropped stream WHEN it reconnects THEN the detail sits under the label`() =
        runComposeUiTest {
            conversationScreen(stateNamed("Stream dropped, reconnecting"))

            onConversation { assertStacked(label = boundsOfReconnecting(), detail = boundsOfReconnectingDetail()) }
        }

    @Test
    fun `GIVEN a turn past a minute WHEN it is still working THEN the detail sits under the label`() =
        runComposeUiTest {
            conversationScreen(stateNamed("Still working after a minute"))

            onConversation { assertStacked(label = boundsOfStillWorking(), detail = boundsOfStillWorkingDetail()) }
        }

    private fun assertStacked(
        label: DpRect,
        detail: DpRect,
    ) {
        assertEquals(label.left, detail.left, "label and detail share a left edge")
        assertTrue(detail.top >= label.bottom, "the detail is on its own row, under the label")
    }

    // ---- choosing the agent of a new chat ----------------------------------

    @Test
    fun `GIVEN a new chat WHEN the avatar is tapped THEN the sheet offers every agent`() =
        runComposeUiTest {
            conversationScreen(stateNamed("New chat"))

            onConversation {
                tapsTheAvatar()

                seesTheAgentPicker()
                seesAgent("Academic Researcher")
                seesAgent("Hardware Scout")
                seesOwnedBy("Liam")
                // Every card says what its agent may do, in words: all three here can search the web.
                seesAgentsAbleToSearchTheWeb(count = 3)
            }
        }

    @Test
    fun `GIVEN a chat that has started WHEN it is shown THEN there is no way to change its agent`() =
        runComposeUiTest {
            conversationScreen(stateNamed("A finished exchange"))

            onConversation { doesNotSeeAWayToChangeTheAgent() }
        }

    @Test
    fun `GIVEN the sheet is open WHEN another agent is picked THEN it is chosen and the sheet closes`() =
        runComposeUiTest {
            var picked: String? = null
            conversationScreen(stateNamed("New chat"), onSelectAgent = { picked = it })

            onConversation {
                tapsTheAvatar()
                picksAgent("Academic Researcher")
            }
            waitForIdle()

            assertEquals("agent-research", picked)
            onConversation { doesNotSeeTheAgentPicker() }
        }

    @Test
    fun `GIVEN the agents did not load WHEN the sheet opens THEN it says so and can ask again`() =
        runComposeUiTest {
            var retried = false
            conversationScreen(stateNamed("New chat, agents didn't load"), onRetryAgents = { retried = true })

            onConversation {
                tapsTheAvatar()

                seesAgentsDidNotLoad()
                tapsTryAgain()
            }
            assertTrue(retried)
        }

    /** Canvas: ToolApproveRemove. The removal reads in red and "Remove" is the answer on the right. */
    @Test
    fun `GIVEN a removal waiting on the member WHEN drawn THEN the card says what goes and when and asks Keep it or Remove`() =
        runComposeUiTest {
            val decided = mutableListOf<Pair<String, Boolean>>()
            conversationScreen(stateNamed("Approving a removal"), onDecide = { id, ok, _ -> decided += id to ok })

            onConversation {
                seesTheRemovalCard()
                seesWords("Print shop")
                seesWords("Fri 11 Sep, 18:00")
                val keep = boundsOfKeep()
                val remove = boundsOfRemove()
                assertTrue(keep.right <= remove.left, "the main action is on the right")

                tapsRemove()
                tapsKeep()
            }

            assertEquals(listOf("c-1" to true, "c-1" to false), decided)
        }

    /** Canvas: RepeatAdd. How often sits under the first date, and a new event has no dates to choose between. */
    @Test
    fun `GIVEN a repeating event to add WHEN drawn THEN the card says how often and asks nothing more`() =
        runComposeUiTest {
            conversationScreen(stateNamed("Approving a repeating event"))

            onConversation {
                seesWords("Tue 29 Sep, 07:00")
                seesWords("Every Tue and Thu until 24 Dec")
                doesNotSeeOnlyThisDate()
            }
        }

    /** Canvas: RepeatRemoveA. The switch starts on what the agent understood; Remove sends the member's pick. */
    @Test
    fun `GIVEN one date of a series to remove WHEN the member picks this and following THEN Remove sends that`() =
        runComposeUiTest {
            val decided = mutableListOf<ProposalDetails?>()
            conversationScreen(
                stateNamed("Removing one date of a series"),
                onDecide = { _, _, edited -> decided += edited },
            )

            onConversation {
                seesWords("Every Tue and Thu until 24 Dec")
                seesOnlyThisDateSelected()
                seesThisAndFollowingNotSelected()
                seesTheNoteForRemovingOnlyThisDate()

                picksThisAndFollowing()
                seesThisAndFollowingSelected()
                seesTheNoteForRemovingThisAndFollowing()
                tapsRemove()
            }

            assertEquals(EventScope.ThisAndFollowing, (decided.single() as ProposalDetails.CalendarEvent).scope)
        }

    @Test
    fun `GIVEN one date of a series to remove WHEN removed as proposed THEN nothing is sent as edited`() =
        runComposeUiTest {
            val decided = mutableListOf<ProposalDetails?>()
            conversationScreen(
                stateNamed("Removing one date of a series"),
                onDecide = { _, _, edited -> decided += edited },
            )

            onConversation {
                picksThisAndFollowing()
                picksOnlyThisDate()
                tapsRemove()
            }

            assertEquals(listOf<ProposalDetails?>(null), decided)
        }

    @Test
    fun `GIVEN one date of a series to change WHEN the member picks this and following and ticks from now on THEN the pick goes with it`() =
        runComposeUiTest {
            val automatic = mutableListOf<ProposalDetails?>()
            conversationScreen(
                stateNamed("Changing one date of a series"),
                onApproveAutomatically = { _, edited -> automatic += edited },
            )

            onConversation {
                picksThisAndFollowing()
                ticksAutoApproveChanging()
                tapsApprove()
            }

            assertEquals(EventScope.ThisAndFollowing, (automatic.single() as ProposalDetails.CalendarEvent).scope)
        }

    @Test
    fun `GIVEN one write approved and one declined WHEN the answer is drawn THEN the fold names both and opens to their record lines`() =
        runComposeUiTest {
            conversationScreen(stateNamed("Approved and declined"))

            onConversation {
                opensTheFoldOfAddedAndDeclined(added = "Dinner together", declined = "Buy flowers")

                seesRecordOfAdding("Dinner together")
                seesRecordOfDeclining("Buy flowers")
            }
        }

    @Test
    fun `GIVEN Send pressed while a card waits WHEN drawn THEN the words stay and the hold line asks for the card first`() =
        runComposeUiTest {
            conversationScreen(stateNamed("Waiting on a card, Send held"))

            onConversation {
                seesHoldLine()
                seesComposerHolding("Also lunch on Sunday?")
                seesWords("Dinner together")
            }
        }

    /** Canvas: ToolFailed. What broke, that nothing was added, and the button that fixes it. */
    @Test
    fun `GIVEN an add the calendar refused WHEN the answer is drawn THEN the card says so and Reconnect calendar opens the connect flow`() =
        runComposeUiTest {
            var connects = 0
            conversationScreen(stateNamed("A tool that failed"), onConnectCalendar = { connects++ })

            onConversation {
                seesTheCalendarRefusedTheAdd()
                seesWhatBrokeAndThatNothingWasAdded()
                // The card says what broke; the red step isn't repeated above it.
                doesNotSeeTheFailedAddAsAStep()

                tapsReconnectCalendar()
            }

            assertEquals(1, connects)
        }

    /** Canvas: ToolFixedCard. The retry icon asks the answer's question again, as a new message. */
    @Test
    fun `GIVEN a fixed step in the latest answer WHEN the card is drawn THEN it says the calendar is connected and Ask again sends the question`() =
        runComposeUiTest {
            val sent = mutableListOf<String>()
            conversationScreen(stateNamed("A fixed tool, the latest answer"), onSend = { sent += it })

            onConversation {
                seesTheCalendarIsConnected()
                doesNotSeeReconnectCalendar()

                tapsAskAgain()
            }

            assertEquals(listOf("Put the print shop cutoff in my calendar"), sent)
        }

    /** Canvas: ToolFixedLater. */
    @Test
    fun `GIVEN a fixed step and a newer message after it WHEN the card is drawn THEN there is no Ask again`() =
        runComposeUiTest {
            conversationScreen(stateNamed("A fixed tool, a newer message after it"))

            onConversation {
                seesTheCalendarIsConnected()
                doesNotSeeAskAgain()
            }
        }

    @Test
    fun `GIVEN an add the calendar refused after other steps WHEN the answer is drawn THEN the red step stays in the fold and the card stays out`() =
        runComposeUiTest {
            conversationScreen(stateNamed("A tool that failed, among other steps"))

            onConversation {
                seesReconnectCalendar()
                opensTheFoldOfTheFailedAdd()

                // Opened, the fold shows the red step under its label; the card is still there.
                seesTheFailedAddTimes(2)
                seesReconnectCalendar()
            }
        }

    // ---- auto-approve from the card (slice 4, PR 5) -------------------------

    /** Canvas: ToolAutoApprove. Ticking the box changes nothing until Approve. */
    @Test
    fun `GIVEN an add waiting on the member WHEN the box is ticked and it is approved THEN it is approved automatically`() =
        runComposeUiTest {
            val decided = mutableListOf<Pair<String, Boolean>>()
            val automatic = mutableListOf<String>()
            conversationScreen(
                stateNamed("Auto-approving an add"),
                onDecide = { id, ok, _ -> decided += id to ok },
                onApproveAutomatically = { id, _ -> automatic += id },
            )

            onConversation {
                seesAutoApproveAddingOff()

                ticksAutoApproveAdding()
                seesAutoApproveAddingOn()
                tapsApprove()
            }

            assertEquals(listOf("c-1"), automatic)
            assertEquals(emptyList(), decided)
        }

    @Test
    fun `GIVEN an add waiting on the member WHEN approved without ticking THEN it is only approved`() =
        runComposeUiTest {
            val decided = mutableListOf<Pair<String, Boolean>>()
            val automatic = mutableListOf<String>()
            conversationScreen(
                stateNamed("Auto-approving an add"),
                onDecide = { id, ok, _ -> decided += id to ok },
                onApproveAutomatically = { id, _ -> automatic += id },
            )

            onConversation { tapsApprove() }

            assertEquals(listOf("c-1" to true), decided)
            assertEquals(emptyList(), automatic)
        }

    @Test
    fun `GIVEN a removal waiting on the member WHEN drawn THEN it never offers to make removing automatic`() =
        runComposeUiTest {
            conversationScreen(stateNamed("Approving a removal"))

            onConversation {
                seesTheRemovalCard()
                doesNotSeeAnOfferToMakeRemovingAutomatic()
            }
        }

    /** Canvas: ToolAutoApproved. */
    @Test
    fun `GIVEN an add made automatic WHEN drawn THEN its record says automatic and Undo asks again`() =
        runComposeUiTest {
            var undone = false
            conversationScreen(stateNamed("Adding is automatic"), onUndoAutomatic = { undone = true })

            onConversation {
                seesRecordOfAddingAutomatically("Print shop cutoff")
                seesAddingIsNowAutomatic()
                tapsUndo()
            }

            assertTrue(undone)
        }

    // ---- edit before approving (slice 4, PR 4) -------------------------------

    private fun ComposeUiTest.editing(decided: MutableList<Triple<String, Boolean, ProposalDetails?>>) =
        conversationScreen(
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
            editing(mutableListOf())

            onConversation {
                val decline = boundsOfDecline()
                val edit = boundsOfEdit()
                val approve = boundsOfApprove()
                assertTrue(decline.right <= edit.left && edit.right <= approve.left, "Decline, Edit, then Approve")

                tapsEdit()

                seesTheTitleField("Dinner together")
                doesNotSeeAFieldHolding("Sat 3 Oct")
                doesNotSeeAFieldHolding("20:00")
                seesTheDay("Sat 3 Oct")
                tapsTheDay()
                seesAPickerOpen()
                dismissesThePicker()
                seesTheTime("20:00")
                tapsTheTime()
                seesAPickerOpen()
            }
        }

    @Test
    fun `GIVEN a card being edited WHEN a time is picked THEN it is marked and Approve with changes sends it`() =
        runComposeUiTest {
            val decided = mutableListOf<Triple<String, Boolean, ProposalDetails?>>()
            editing(decided)

            onConversation {
                tapsEdit()

                tapsTheTime()
                picksHour(21)
                movesOnToMinutes()
                picksMinutes(30)
                confirmsThePicker()

                seesTheTime("21:30")
                seesTheTimeMarkedChanged()
                tapsApproveWithChanges()
            }
            assertEquals(sent(EventMoment(2026, 10, 3, hour = 21, minute = 30)), decided)
        }

    @Test
    fun `GIVEN a card being edited WHEN a day is picked THEN it is marked and Approve with changes sends it`() =
        runComposeUiTest {
            val decided = mutableListOf<Triple<String, Boolean, ProposalDetails?>>()
            editing(decided)

            onConversation {
                tapsEdit()

                tapsTheDay()
                picksDay(weekday = "Sunday", day = 4, month = "October", year = 2026)
                confirmsThePicker()

                seesTheDay("Sun 4 Oct")
                seesTheDayMarkedChanged()
                tapsApproveWithChanges()
            }
            assertEquals(sent(EventMoment(2026, 10, 4, hour = 20, minute = 0)), decided)
        }

    @Test
    fun `GIVEN a picker open WHEN it is dismissed THEN the field stays as proposed`() =
        runComposeUiTest {
            editing(mutableListOf())

            onConversation {
                tapsEdit()

                tapsTheTime()
                picksHour(21)
                dismissesThePicker()

                seesTheTime("20:00")
                doesNotSeeApproveWithChanges()
            }
        }

    @Test
    fun `GIVEN a card being edited WHEN the title is emptied THEN Approve says why and sends nothing`() =
        runComposeUiTest {
            val decided = mutableListOf<Triple<String, Boolean, ProposalDetails?>>()
            editing(decided)

            onConversation {
                tapsEdit()

                retitles(from = "Dinner together", to = "")
                doesNotSeeTheTitleIsMissing()
                tapsApproveWithChanges()

                seesTheTitleIsMissing()
            }
            assertEquals(emptyList(), decided)
        }

    @Test
    fun `GIVEN a card being edited WHEN Cancel is tapped THEN the edits go and plain Approve sends no edit`() =
        runComposeUiTest {
            val decided = mutableListOf<Triple<String, Boolean, ProposalDetails?>>()
            editing(decided)

            onConversation {
                tapsEdit()
                retitles(from = "Dinner together", to = "Dinner out")
                tapsCancel()

                seesWords("Dinner together")
                seesWords("Sat 3 Oct, 20:00")
                tapsApprove()
            }

            assertEquals(listOf(Triple<String, Boolean, ProposalDetails?>("c-1", true, null)), decided)
        }

    @Test
    fun `GIVEN a removal waiting on the member WHEN drawn THEN it offers no Edit`() =
        runComposeUiTest {
            conversationScreen(stateNamed("Approving a removal"))

            onConversation { doesNotSeeEdit() }
        }

    /** Canvas: RepeatChange with Edit. The which-dates pick and the edits go together. */
    @Test
    fun `GIVEN this and following picked on one date of a series WHEN it is edited and approved THEN both the edit and the pick are sent`() =
        runComposeUiTest {
            val decided = mutableListOf<ProposalDetails?>()
            conversationScreen(
                stateNamed("Changing one date of a series"),
                onDecide = { _, _, edited -> decided += edited },
            )

            onConversation {
                picksThisAndFollowing(scrolling = true)
                tapsEdit(scrolling = true)
                retitles(from = "Gym", to = "Swim")
                tapsApproveWithChanges(scrolling = true)
            }

            val sent = decided.single() as ProposalDetails.CalendarEvent
            assertEquals("Swim", sent.title)
            assertEquals(EventScope.ThisAndFollowing, sent.scope)
        }

    @Test
    fun `GIVEN one date of a series WHEN only its title is edited THEN it stays a change to that one date`() =
        runComposeUiTest {
            val decided = mutableListOf<ProposalDetails?>()
            conversationScreen(
                stateNamed("Changing one date of a series"),
                onDecide = { _, _, edited -> decided += edited },
            )

            onConversation {
                tapsEdit(scrolling = true)
                retitles(from = "Gym", to = "Swim")
                tapsApproveWithChanges(scrolling = true)
            }

            val sent = decided.single() as ProposalDetails.CalendarEvent
            assertEquals("Swim", sent.title)
            assertEquals(EventScope.OnlyThis, sent.scope)
        }

    @Test
    fun `GIVEN one date of a series being edited WHEN drawn THEN the switch is there with the pick and changing it there is what is sent`() =
        runComposeUiTest {
            val decided = mutableListOf<ProposalDetails?>()
            conversationScreen(
                stateNamed("Changing one date of a series"),
                onDecide = { _, _, edited -> decided += edited },
            )

            onConversation {
                picksThisAndFollowing(scrolling = true)
                tapsEdit(scrolling = true)
                seesThisAndFollowingSelected(scrolling = true)

                picksOnlyThisDate(scrolling = true)
                retitles(from = "Gym", to = "Swim")
                tapsApproveWithChanges(scrolling = true)
            }

            assertEquals(EventScope.OnlyThis, (decided.single() as ProposalDetails.CalendarEvent).scope)
        }

    // ---- Repeat and Ends in the Edit view (slice 4) ---------------------------

    private fun ComposeUiTest.repeating(decided: MutableList<ProposalDetails?>) =
        conversationScreen(
            stateNamed("Approving a repeating event"),
            onDecide = { _, _, edited -> decided += edited },
        )

    private fun repeatSent(decided: List<ProposalDetails?>): RepeatChange? =
        (decided.single() as ProposalDetails.CalendarEvent).repeatChange

    /** Canvas: RepeatEdit and RepeatEndsPicker. The fields start on the rule as proposed, counted out underneath. */
    @Test
    fun `GIVEN a repeating event to add WHEN Edit is tapped THEN Repeat and Ends show the rule and the caption counts it`() =
        runComposeUiTest {
            repeating(mutableListOf())

            onConversation {
                tapsEdit(scrolling = true)

                seesRepeatWeeklySelected()
                seesWeekdayOn(Weekday.Tuesday)
                seesWeekdayOn(Weekday.Thursday)
                seesWeekdayOff(Weekday.Wednesday)
                seesEndsOnDateSelected()
                seesTheLastDate("Thu 24 Dec")
                seesTheSessionsCounted(sessions = 26, last = "Thu 24 Dec")
                doesNotSeeApproveWithChanges()
            }
        }

    /** Canvas: RepeatEndsAfter. After starts at ten; one more makes eleven, and that is what is sent. */
    @Test
    fun `GIVEN a repeating event being edited WHEN Ends is set to After and stepped up THEN the count is sent`() =
        runComposeUiTest {
            val decided = mutableListOf<ProposalDetails?>()
            repeating(decided)

            onConversation {
                tapsEdit(scrolling = true)

                picksEndsAfter()
                seesTimes(10)
                seesTheLastOneLandingOn("Thu 29 Oct")
                tapsOneMore()
                seesTimes(11)
                tapsApproveWithChanges(scrolling = true)
            }

            val rule = EventRepeat(every = RepeatEvery.Week, weekdays = listOf(1, 3), count = 11)
            assertEquals(RepeatChange.To(rule), repeatSent(decided))
        }

    @Test
    fun `GIVEN a weekly event being edited WHEN a day chip is ticked THEN the days sent include it`() =
        runComposeUiTest {
            val decided = mutableListOf<ProposalDetails?>()
            repeating(decided)

            onConversation {
                tapsEdit(scrolling = true)

                tapsWeekday(Weekday.Wednesday)
                seesWeekdayOn(Weekday.Wednesday)
                tapsApproveWithChanges(scrolling = true)
            }

            val rule =
                EventRepeat(
                    every = RepeatEvery.Week,
                    weekdays = listOf(1, 2, 3),
                    until = EventMoment(2026, 12, 24),
                )
            assertEquals(RepeatChange.To(rule), repeatSent(decided))
        }

    @Test
    fun `GIVEN a weekly event being edited WHEN every day is unticked THEN Approve says why and sends nothing`() =
        runComposeUiTest {
            val decided = mutableListOf<ProposalDetails?>()
            repeating(decided)

            onConversation {
                tapsEdit(scrolling = true)

                tapsWeekday(Weekday.Tuesday)
                tapsWeekday(Weekday.Thursday)
                tapsApproveWithChanges(scrolling = true)

                seesNoDaysAreTicked()
            }
            assertEquals(emptyList(), decided)
        }

    /** A one-off add starts on None; picking Daily makes it repeat, forever until an end is picked. */
    @Test
    fun `GIVEN a one-off add being edited WHEN Daily is picked THEN it is sent as repeating every day`() =
        runComposeUiTest {
            val decided = mutableListOf<Triple<String, Boolean, ProposalDetails?>>()
            editing(decided)

            onConversation {
                tapsEdit(scrolling = true)

                seesRepeatNoneSelected()
                doesNotSeeEndsNever()
                picksRepeatDaily()
                seesEndsNeverSelected()
                tapsApproveWithChanges(scrolling = true)
            }

            val sent = decided.single().third as ProposalDetails.CalendarEvent
            assertEquals(RepeatChange.To(EventRepeat(every = RepeatEvery.Day)), sent.repeatChange)
        }

    /** One date can't have a rule of its own; switching to this and following opens the fields, with no None. */
    @Test
    fun `GIVEN one date of a series being edited WHEN this and following is picked THEN Repeat shows without None`() =
        runComposeUiTest {
            conversationScreen(stateNamed("Changing one date of a series"))

            onConversation {
                tapsEdit(scrolling = true)

                doesNotSeeRepeatWeekly()
                picksThisAndFollowing(scrolling = true)

                seesRepeatWeeklySelected()
                doesNotSeeRepeatNone()
            }
        }

    // ---- remember and forget (slice 5, PR 6) ----------------------------------------------------

    private val vegetarian = "Sam is vegetarian and doesn’t eat fish."

    /** Canvas: MemRemember. Your own memory is replaced, and the card says which one with its age. */
    @Test
    fun `GIVEN a remember over your own memory WHEN drawn THEN it shows what it replaces and offers to stop asking`() =
        runComposeUiTest {
            val decided = mutableListOf<Triple<String, Boolean, ProposalDetails?>>()
            conversationScreen(
                stateNamed("Remembering over your own memory"),
                onDecide = { id, ok, edit -> decided += Triple(id, ok, edit) },
            )

            onConversation {
                seesLookedThroughWhatsKnown()
                seesTheRememberCard()
                seesWords("Sam is vegan now.")
                seesItIsForTheHousehold()
                seesItReplaces(vegetarian, meta = "You · Planner · March")
                seesAnOfferToRememberWithoutAsking()
                tapsApprove()
            }

            assertEquals(listOf(Triple<String, Boolean, ProposalDetails?>("c-1", true, null)), decided)
        }

    /** Canvas: MemRememberOther. Liam's stays: the card says so, and that he will be asked. */
    @Test
    fun `GIVEN a remember beside Liams memory WHEN drawn THEN it names him and says his is kept and he is asked`() =
        runComposeUiTest {
            conversationScreen(stateNamed("Remembering beside Liam's memory"))

            onConversation {
                seesWhatTheySaid("Liam", vegetarian, meta = "Planner · March")
                seesTheirsIsKeptAndTheyWillBeAsked("Liam")
                doesNotSeeItReplaces()
            }
        }

    @Test
    fun `GIVEN a remember being edited WHEN Yours is picked THEN Approve with changes sends only whose it is`() =
        runComposeUiTest {
            val decided = mutableListOf<ProposalDetails?>()
            conversationScreen(
                stateNamed("Remembering over your own memory"),
                onDecide = { _, _, edit -> decided += edit },
            )

            onConversation {
                tapsEdit(scrolling = true)

                seesTheTitleField("Sam is vegan now.")
                seesHouseholdSelected()
                picksYours()
                tapsApproveWithChanges(scrolling = true)
            }

            assertEquals(listOf<ProposalDetails?>(ProposalDetails.Memory(content = null, household = false)), decided)
        }

    /** Kept beside Liam's, it is the household's whatever the switch says, so there is no switch. */
    @Test
    fun `GIVEN a remember beside Liams memory being edited WHEN drawn THEN only the sentence opens`() =
        runComposeUiTest {
            conversationScreen(stateNamed("Remembering beside Liam's memory"))

            onConversation {
                tapsEdit(scrolling = true)

                seesTheTitleField("Sam is vegan now.")
                doesNotSeeWhoseItIs()
            }
        }

    /** Canvas: MemForget. Red, Keep it on the left, Forget on the right; it always asks and has no Edit. */
    @Test
    fun `GIVEN a forget WHEN drawn THEN it shows the memory with whose and when and asks Keep it or Forget`() =
        runComposeUiTest {
            val decided = mutableListOf<Pair<String, Boolean>>()
            conversationScreen(stateNamed("Forgetting a memory"), onDecide = { id, ok, _ -> decided += id to ok })

            onConversation {
                seesTheForgetCard()
                seesWords("Studio Jury on Friday 17 October, 09:00.")
                seesWords("Yours · Researcher · March")
                doesNotSeeEdit()
                doesNotSeeAnOfferToMakeRemovingAutomatic()
                assertTrue(boundsOfKeep().right <= boundsOfForget().left, "Forget is on the right")

                tapsForget()
                tapsKeep()
            }

            assertEquals(listOf("c-1" to true, "c-1" to false), decided)
        }
}
