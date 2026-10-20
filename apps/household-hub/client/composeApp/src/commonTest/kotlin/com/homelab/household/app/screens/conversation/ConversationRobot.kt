package com.homelab.household.app.screens.conversation

import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.compose.ui.unit.DpRect
import com.homelab.household.app.components.NOT_SENT_GLYPH_TAG
import com.homelab.household.app.components.PICKER_CONFIRM_TAG
import com.homelab.household.app.components.PICKER_DISMISS_TAG
import com.homelab.household.app.components.RETRY_GLYPH_TAG
import com.homelab.household.app.components.SENT_GLYPH_TAG
import com.homelab.household.app.components.THINKING_DOTS_TAG
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.agent_owner_member
import com.homelab.household.app.resources.agent_picker_failed_title
import com.homelab.household.app.resources.agent_picker_title
import com.homelab.household.app.resources.chat_looked_over_nothing
import com.homelab.household.app.resources.chat_looked_over_remembered
import com.homelab.household.app.resources.conversation_change_agent
import com.homelab.household.app.resources.conversation_composer_leave
import com.homelab.household.app.resources.conversation_composer_waiting
import com.homelab.household.app.resources.conversation_failed_line
import com.homelab.household.app.resources.conversation_failed_title
import com.homelab.household.app.resources.conversation_greeting
import com.homelab.household.app.resources.conversation_not_sent
import com.homelab.household.app.resources.conversation_reconnecting
import com.homelab.household.app.resources.conversation_reconnecting_detail
import com.homelab.household.app.resources.conversation_retry
import com.homelab.household.app.resources.conversation_sent
import com.homelab.household.app.resources.conversation_steps_show
import com.homelab.household.app.resources.conversation_still_working
import com.homelab.household.app.resources.conversation_still_working_detail
import com.homelab.household.app.resources.conversation_thinking
import com.homelab.household.app.resources.conversation_thought
import com.homelab.household.app.resources.conversation_try_again
import com.homelab.household.app.resources.field_changed
import com.homelab.household.app.resources.memories_tab_household_plain
import com.homelab.household.app.resources.memories_tab_yours_plain
import com.homelab.household.app.resources.profile_memories_to_check
import com.homelab.household.app.resources.send_message
import com.homelab.household.app.resources.steps_label_and
import com.homelab.household.app.resources.steps_label_failed
import com.homelab.household.app.resources.steps_phrase_added
import com.homelab.household.app.resources.steps_phrase_not_added
import com.homelab.household.app.resources.steps_phrase_read
import com.homelab.household.app.resources.steps_phrase_searched
import com.homelab.household.app.resources.tool_automatic_undo
import com.homelab.household.app.resources.tool_calendar_add_automatic_ask
import com.homelab.household.app.resources.tool_calendar_add_declined
import com.homelab.household.app.resources.tool_calendar_add_done
import com.homelab.household.app.resources.tool_calendar_add_failed
import com.homelab.household.app.resources.tool_calendar_add_now_automatic
import com.homelab.household.app.resources.tool_calendar_change_automatic_ask
import com.homelab.household.app.resources.tool_calendar_read_done
import com.homelab.household.app.resources.tool_calendar_read_failed
import com.homelab.household.app.resources.tool_calendar_read_running
import com.homelab.household.app.resources.tool_calendar_remove_card
import com.homelab.household.app.resources.tool_card_approve
import com.homelab.household.app.resources.tool_card_approve_with_changes
import com.homelab.household.app.resources.tool_card_cancel
import com.homelab.household.app.resources.tool_card_decline
import com.homelab.household.app.resources.tool_card_edit
import com.homelab.household.app.resources.tool_card_ends_after
import com.homelab.household.app.resources.tool_card_ends_never
import com.homelab.household.app.resources.tool_card_ends_on_date
import com.homelab.household.app.resources.tool_card_field_day
import com.homelab.household.app.resources.tool_card_field_empty
import com.homelab.household.app.resources.tool_card_field_time
import com.homelab.household.app.resources.tool_card_forget
import com.homelab.household.app.resources.tool_card_hold
import com.homelab.household.app.resources.tool_card_keep
import com.homelab.household.app.resources.tool_card_memory_household
import com.homelab.household.app.resources.tool_card_memory_kept_too
import com.homelab.household.app.resources.tool_card_memory_replaces
import com.homelab.household.app.resources.tool_card_memory_said
import com.homelab.household.app.resources.tool_card_memory_waiting_on
import com.homelab.household.app.resources.tool_card_remove
import com.homelab.household.app.resources.tool_card_repeat_daily
import com.homelab.household.app.resources.tool_card_repeat_last
import com.homelab.household.app.resources.tool_card_repeat_no_days
import com.homelab.household.app.resources.tool_card_repeat_none
import com.homelab.household.app.resources.tool_card_repeat_sessions
import com.homelab.household.app.resources.tool_card_repeat_weekly
import com.homelab.household.app.resources.tool_card_scope_following
import com.homelab.household.app.resources.tool_card_scope_following_remove_note
import com.homelab.household.app.resources.tool_card_scope_this
import com.homelab.household.app.resources.tool_card_scope_this_remove_note
import com.homelab.household.app.resources.tool_card_times
import com.homelab.household.app.resources.tool_card_times_more
import com.homelab.household.app.resources.tool_card_weekday_fri
import com.homelab.household.app.resources.tool_card_weekday_mon
import com.homelab.household.app.resources.tool_card_weekday_sat
import com.homelab.household.app.resources.tool_card_weekday_sun
import com.homelab.household.app.resources.tool_card_weekday_thu
import com.homelab.household.app.resources.tool_card_weekday_tue
import com.homelab.household.app.resources.tool_card_weekday_wed
import com.homelab.household.app.resources.tool_fix_ask_again
import com.homelab.household.app.resources.tool_fix_calendar_fixed_title
import com.homelab.household.app.resources.tool_fix_calendar_rejected_detail
import com.homelab.household.app.resources.tool_fix_calendar_rejected_title
import com.homelab.household.app.resources.tool_fix_nothing_added
import com.homelab.household.app.resources.tool_fix_reconnect_calendar
import com.homelab.household.app.resources.tool_forget_card
import com.homelab.household.app.resources.tool_memory_lookup_done
import com.homelab.household.app.resources.tool_permission_search
import com.homelab.household.app.resources.tool_read_page_done
import com.homelab.household.app.resources.tool_read_page_done_named
import com.homelab.household.app.resources.tool_read_page_failed_named
import com.homelab.household.app.resources.tool_reason_blocked
import com.homelab.household.app.resources.tool_remember_automatic_ask
import com.homelab.household.app.resources.tool_remember_card
import com.homelab.household.app.resources.tool_search_done_query
import com.homelab.household.app.resources.tool_search_results
import com.homelab.household.app.resources.tool_step_automatic
import com.homelab.household.app.resources.tool_step_named
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.domain.model.EventTime
import com.homelab.household.domain.model.ProposalDetails
import com.homelab.household.presentation.chatsession.ChatSessionUiState
import com.homelab.household.presentation.memories.LocalEventTime
import com.homelab.household.presentation.memories.MemoryAge
import com.homelab.household.presentation.memories.localEventTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import kotlin.test.assertEquals
import kotlin.time.Instant

/** Everything a conversation says and does, named as the resources it reads. */
@OptIn(ExperimentalTestApi::class)
class ConversationRobot(
    private val test: ComposeUiTest,
) {
    // ---- the page: who you are talking to, and what has been said ------------------------------

    suspend fun seesGreeting(memberName: String) = seesText(getString(Res.string.conversation_greeting, memberName))

    fun seesAgent(name: String) = seesText(name)

    fun seesTagline(tagline: String) = seesText(tagline)

    /** Words the hub or the agent supplied, whole or by a [fragment] of them when [partly]. */
    fun seesWords(
        words: String,
        partly: Boolean = false,
    ) = seesText(words, substring = partly)

    fun doesNotSeeWords(
        words: String,
        partly: Boolean = false,
    ) {
        node(words, substring = partly).assertDoesNotExist()
    }

    /** Taps the conversation itself, away from the composer. */
    fun tapsWordsAwayFromTheComposer(words: String) {
        node(words).performTouchInput { click() }
    }

    fun dragsTheTranscriptDown() {
        test.onNodeWithTag(CONVERSATION_TRANSCRIPT_TAG).performTouchInput { swipeDown() }
    }

    fun seesError(message: String) = seesText(message)

    // ---- the question: sent, or not ------------------------------------------------------------

    suspend fun seesSent() = seesText(getString(Res.string.conversation_sent))

    suspend fun doesNotSeeSent() {
        node(getString(Res.string.conversation_sent)).assertDoesNotExist()
    }

    suspend fun seesNotSent() = seesText(getString(Res.string.conversation_not_sent))

    suspend fun doesNotSeeNotSent() {
        node(getString(Res.string.conversation_not_sent)).assertDoesNotExist()
    }

    suspend fun tapsRetry() {
        node(getString(Res.string.conversation_retry)).performClick()
    }

    suspend fun doesNotSeeRetry() {
        node(getString(Res.string.conversation_retry)).assertDoesNotExist()
    }

    fun seesTheNotSentGlyph() {
        waitForOne(hasTestTag(NOT_SENT_GLYPH_TAG), unmerged = true)
        test.onNodeWithTag(NOT_SENT_GLYPH_TAG, useUnmergedTree = true).assertExists()
    }

    fun seesTheRetryGlyph() {
        waitForOne(hasTestTag(RETRY_GLYPH_TAG), unmerged = true)
        test.onNodeWithTag(RETRY_GLYPH_TAG, useUnmergedTree = true).assertExists()
    }

    /** The contrast of the check beside "Sent", measured from its rendered pixels. */
    fun contrastOfTheSentCheck(): Double {
        waitForOne(hasTestTag(SENT_GLYPH_TAG))
        return contrastWithin(test.onNodeWithTag(SENT_GLYPH_TAG).captureToImage().toPixelMap())
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

    // ---- the status of a turn being answered ---------------------------------------------------

    suspend fun seesThinking() = seesText(getString(Res.string.conversation_thinking))

    fun seesThinkingDots() {
        waitForOne(hasTestTag(THINKING_DOTS_TAG))
        test.onNodeWithTag(THINKING_DOTS_TAG).assertIsDisplayed()
    }

    fun doesNotSeeThinkingDots() {
        test.onNodeWithTag(THINKING_DOTS_TAG).assertDoesNotExist()
    }

    suspend fun seesReconnecting() = seesText(getString(Res.string.conversation_reconnecting))

    suspend fun seesStillWorking() = seesText(getString(Res.string.conversation_still_working))

    suspend fun seesCheckingYourCalendar() = seesText(getString(Res.string.tool_calendar_read_running))

    suspend fun seesCalendarCouldNotBeChecked() = seesText(getString(Res.string.tool_calendar_read_failed))

    suspend fun seesThought(seconds: Int) = seesText(getString(Res.string.conversation_thought, seconds))

    suspend fun doesNotSeeThought(seconds: Int) {
        node(getString(Res.string.conversation_thought, seconds)).assertDoesNotExist()
    }

    suspend fun seesAnswerFailed() = seesText(getString(Res.string.conversation_failed_title))

    suspend fun seesTheQuestionArrived() = seesText(getString(Res.string.conversation_failed_line))

    suspend fun tapsTryAgain() {
        node(getString(Res.string.conversation_try_again)).performClick()
    }

    suspend fun doesNotSeeTryAgain() {
        node(getString(Res.string.conversation_try_again)).assertDoesNotExist()
    }

    // ---- the quiet line after a look-over -----------------------------------------------------

    suspend fun seesTheLookedOverLine(
        remembered: Int,
        toCheck: Int,
        time: String,
    ) {
        seesText(getString(Res.string.chat_looked_over_remembered, remembered), unmerged = true)
        seesText(getString(Res.string.profile_memories_to_check, toCheck), unmerged = true)
        seesText(time, unmerged = true)
    }

    suspend fun seesNothingNewAt(time: String) =
        seesText(getString(Res.string.chat_looked_over_nothing, time), unmerged = true)

    fun tapsTheLookedOverLine() {
        test.onNodeWithTag(LOOKED_OVER_LINE_TAG).performClick()
    }

    fun boundsOfTheLookedOverLine(): DpRect = test.onNodeWithTag(LOOKED_OVER_LINE_TAG).getUnclippedBoundsInRoot()

    // ---- where each piece sits -----------------------------------------------------------------

    fun boundsOfWords(
        words: String,
        partly: Boolean = false,
    ): DpRect = boundsOf(words, substring = partly)

    suspend fun boundsOfThought(seconds: Int) = boundsOf(getString(Res.string.conversation_thought, seconds))

    suspend fun boundsOfCalendarChecked() = boundsOf(getString(Res.string.tool_calendar_read_done))

    fun boundsOfTheDots(): DpRect {
        waitForOne(hasTestTag(THINKING_DOTS_TAG))
        return test.onNodeWithTag(THINKING_DOTS_TAG).getUnclippedBoundsInRoot()
    }

    /** The word under the dots, which is its own node inside the status. */
    suspend fun boundsOfThinking() = boundsOf(getString(Res.string.conversation_thinking), unmerged = true)

    suspend fun boundsOfNotSent() = boundsOf(getString(Res.string.conversation_not_sent))

    /** The Retry link as the eye sees it, beside "Not sent". */
    suspend fun boundsOfRetryLink() = boundsOf(getString(Res.string.conversation_retry), unmerged = true)

    /** The Retry link as a thumb finds it. */
    suspend fun boundsOfRetryTarget() = boundsOf(getString(Res.string.conversation_retry))

    suspend fun boundsOfReconnecting() = boundsOf(getString(Res.string.conversation_reconnecting))

    suspend fun boundsOfReconnectingDetail() = boundsOf(getString(Res.string.conversation_reconnecting_detail))

    suspend fun boundsOfStillWorking() = boundsOf(getString(Res.string.conversation_still_working))

    suspend fun boundsOfStillWorkingDetail() = boundsOf(getString(Res.string.conversation_still_working_detail))

    // ---- folded steps --------------------------------------------------------------------------

    suspend fun tapsCalendarChecked() {
        node(getString(Res.string.tool_calendar_read_done)).performClick()
    }

    suspend fun tapsTheFirstCalendarChecked() {
        test.onAllNodesWithText(getString(Res.string.tool_calendar_read_done))[0].performClick()
    }

    suspend fun seesCalendarCheckedTimes(times: Int) {
        test.onAllNodesWithText(getString(Res.string.tool_calendar_read_done)).assertCountEquals(times)
    }

    /** "Searched the web, read 2 pages · 1 failed", with how many steps it holds told to a screen reader. */
    suspend fun seesResearchFold(
        pagesRead: Int,
        failed: Int,
        steps: Int,
    ) {
        val said =
            getString(
                Res.string.steps_label_and,
                getString(Res.string.steps_phrase_searched),
                getPluralString(Res.plurals.steps_phrase_read, pagesRead, pagesRead),
            ).replaceFirstChar { it.uppercase() }
        val fold = seesText("$said$SEPARATOR${getString(Res.string.steps_label_failed, failed)}")
        assertEquals(
            getPluralString(Res.plurals.conversation_steps_show, steps, steps),
            fold.fetchSemanticsNode().config[SemanticsActions.OnClick].label,
        )
    }

    /** Opens the fold of a write that was approved and one that was declined, naming both. */
    suspend fun opensTheFoldOfAddedAndDeclined(
        added: String,
        declined: String,
    ) {
        val said =
            getString(
                Res.string.steps_label_and,
                getString(Res.string.steps_phrase_added, added),
                getString(Res.string.steps_phrase_not_added, declined),
            ).replaceFirstChar { it.uppercase() }
        seesText(said).performClick()
    }

    suspend fun seesRecordOfAdding(title: String) =
        seesText(getString(Res.string.tool_step_named, getString(Res.string.tool_calendar_add_done), title))

    suspend fun seesRecordOfAddingAutomatically(title: String) =
        seesText(
            getString(Res.string.tool_step_named, getString(Res.string.tool_calendar_add_done), title) +
                getString(Res.string.tool_step_automatic, ""),
        )

    suspend fun seesRecordOfDeclining(title: String) =
        seesText(getString(Res.string.tool_step_named, getString(Res.string.tool_calendar_add_declined), title))

    // ---- research steps ------------------------------------------------------------------------

    suspend fun seesSearched(
        query: String,
        results: Int,
    ) = seesText(
        getString(
            Res.string.tool_search_done_query,
            query,
            getPluralString(Res.plurals.tool_search_results, results, results),
        ),
    )

    /** The page's title carries the link; the site is kept whole after it. */
    suspend fun seesRead(
        title: String,
        host: String,
    ) {
        val (named, kept) = readLine(title, host)
        seesText(named)
        seesText(kept, unmerged = true)
    }

    suspend fun doesNotSeeRead(
        title: String,
        host: String,
    ) {
        node(readLine(title, host).first).assertDoesNotExist()
    }

    suspend fun seesCouldNotReadBecauseItBlocks(host: String) =
        seesText(
            getString(
                Res.string.tool_step_named,
                getString(Res.string.tool_read_page_failed_named, host),
                getString(Res.string.tool_reason_blocked),
            ),
        )

    suspend fun doesNotSeeReadAPage() {
        node(getString(Res.string.tool_read_page_done)).assertDoesNotExist()
    }

    private suspend fun readLine(
        title: String,
        host: String,
    ): Pair<String, String> {
        val line = getString(Res.string.tool_read_page_done_named, title, host)
        val end = line.indexOf(title) + title.length
        return line.substring(0, end) to line.substring(end)
    }

    // ---- the composer --------------------------------------------------------------------------

    suspend fun seesComposerWaiting() = seesText(getString(Res.string.conversation_composer_waiting))

    suspend fun seesComposerSaysYouCanLeave() = seesText(getString(Res.string.conversation_composer_leave))

    fun seesComposerHolding(text: String) {
        composer().assertTextContains(text)
    }

    fun tapsTheComposer() {
        composer().performClick()
    }

    fun seesTheComposerFocused() {
        composer().assertIsFocused()
    }

    fun seesTheComposerNotFocused() {
        composer().assertIsNotFocused()
    }

    suspend fun tapsSend() {
        test.onNodeWithContentDescription(getString(Res.string.send_message)).performClick()
    }

    fun pressesSendOnTheKeyboard() {
        composer().performImeAction()
    }

    private fun composer() = test.onNode(hasSetTextAction())

    // ---- choosing the agent of a new chat ------------------------------------------------------

    suspend fun tapsTheAvatar() {
        test.onNodeWithContentDescription(getString(Res.string.conversation_change_agent)).performClick()
    }

    suspend fun doesNotSeeAWayToChangeTheAgent() {
        test.onAllNodes(hasContentDescription(getString(Res.string.conversation_change_agent))).assertCountEquals(0)
    }

    suspend fun seesTheAgentPicker() = seesText(getString(Res.string.agent_picker_title))

    suspend fun doesNotSeeTheAgentPicker() {
        node(getString(Res.string.agent_picker_title)).assertDoesNotExist()
    }

    suspend fun seesOwnedBy(member: String) = seesText(getString(Res.string.agent_owner_member, member))

    /** Every card says what its agent may do, in words. */
    suspend fun seesAgentsAbleToSearchTheWeb(count: Int) {
        val searches = hasText(getString(Res.string.tool_permission_search))
        waitForCount(searches, count)
        test.onAllNodes(searches, useUnmergedTree = true).assertCountEquals(count)
    }

    fun picksAgent(name: String) {
        node(name).performClick()
    }

    suspend fun seesAgentsDidNotLoad() = seesText(getString(Res.string.agent_picker_failed_title))

    // ---- a card waiting on the member ----------------------------------------------------------

    suspend fun seesTheRemovalCard() = seesText(getString(Res.string.tool_calendar_remove_card))

    suspend fun doesNotSeeAnOfferToMakeRemovingAutomatic() {
        // Every offer to make a step automatic starts with the same word.
        val offer = getString(Res.string.tool_calendar_add_automatic_ask).substringBefore(' ')
        test.onAllNodes(hasText(offer, substring = true)).assertCountEquals(0)
    }

    suspend fun tapsKeep() {
        node(getString(Res.string.tool_card_keep)).performClick()
    }

    suspend fun tapsRemove() {
        node(getString(Res.string.tool_card_remove)).performClick()
    }

    suspend fun tapsApprove() {
        node(getString(Res.string.tool_card_approve)).performClick()
    }

    suspend fun boundsOfKeep() = boundsOf(getString(Res.string.tool_card_keep))

    suspend fun boundsOfRemove() = boundsOf(getString(Res.string.tool_card_remove))

    suspend fun boundsOfDecline() = boundsOf(getString(Res.string.tool_card_decline))

    suspend fun boundsOfEdit() = boundsOf(getString(Res.string.tool_card_edit))

    suspend fun boundsOfApprove() = boundsOf(getString(Res.string.tool_card_approve))

    suspend fun seesHoldLine() = seesText(getString(Res.string.tool_card_hold))

    suspend fun doesNotSeeEdit() {
        node(getString(Res.string.tool_card_edit)).assertDoesNotExist()
    }

    // ---- remembering and forgetting -----------------------------------------------------------

    suspend fun seesTheRememberCard() = seesText(getString(Res.string.tool_remember_card))

    suspend fun seesLookedThroughWhatsKnown() = seesText(getString(Res.string.tool_memory_lookup_done))

    suspend fun seesItIsForTheHousehold() = seesText(getString(Res.string.tool_card_memory_household))

    suspend fun seesItReplaces(
        stored: String,
        meta: String,
    ) {
        seesText(getString(Res.string.tool_card_memory_replaces).uppercase())
        seesText(stored)
        seesText(meta)
    }

    suspend fun doesNotSeeItReplaces() {
        node(getString(Res.string.tool_card_memory_replaces).uppercase()).assertDoesNotExist()
    }

    suspend fun seesWhatTheySaid(
        name: String,
        stored: String,
        meta: String,
    ) {
        seesText(getString(Res.string.tool_card_memory_said, name).uppercase())
        seesText(stored)
        seesText(meta)
    }

    suspend fun seesTheirsIsKeptAndTheyWillBeAsked(name: String) =
        seesText(getString(Res.string.tool_card_memory_kept_too, name))

    suspend fun seesAnOfferToRememberWithoutAsking() = seesText(getString(Res.string.tool_remember_automatic_ask))

    /** A Forget card's caption for a sentence not stored yet: "Yours · Planner · March · waiting on Liam". */
    suspend fun seesItWaitsOn(
        name: String,
        meta: String,
    ) = seesText("$meta · ${getString(Res.string.tool_card_memory_waiting_on, name)}")

    /** The card's title says Forget too, so the button is the one that can be tapped. */
    private suspend fun forgetButton(): SemanticsNodeInteraction {
        val forget = hasText(getString(Res.string.tool_card_forget)) and hasClickAction()
        waitForOne(forget)
        return test.onNode(forget)
    }

    suspend fun seesTheForgetCard() {
        test.onAllNodesWithText(getString(Res.string.tool_forget_card)).assertCountEquals(2)
        forgetButton().assertIsDisplayed()
    }

    suspend fun tapsForget() {
        forgetButton().performClick()
    }

    suspend fun boundsOfForget() = forgetButton().getUnclippedBoundsInRoot()

    /** Yours or Household, in a Remember card's Edit view. */
    suspend fun picksYours() {
        node(getString(Res.string.memories_tab_yours_plain)).performClick()
    }

    suspend fun seesHouseholdSelected() {
        seesSelected(getString(Res.string.memories_tab_household_plain), selected = true)
    }

    suspend fun doesNotSeeWhoseItIs() {
        node(getString(Res.string.memories_tab_yours_plain)).assertDoesNotExist()
    }

    // ---- which dates of a series ---------------------------------------------------------------

    suspend fun seesOnlyThisDateSelected() {
        seesSelected(getString(Res.string.tool_card_scope_this), selected = true)
    }

    suspend fun seesThisAndFollowingSelected(scrolling: Boolean = false) {
        seesSelected(getString(Res.string.tool_card_scope_following), selected = true, scrolling = scrolling)
    }

    suspend fun seesThisAndFollowingNotSelected() {
        seesSelected(getString(Res.string.tool_card_scope_following), selected = false)
    }

    suspend fun picksOnlyThisDate(scrolling: Boolean = false) {
        item(getString(Res.string.tool_card_scope_this), scrolling).performClick()
    }

    suspend fun picksThisAndFollowing(scrolling: Boolean = false) {
        item(getString(Res.string.tool_card_scope_following), scrolling).performClick()
    }

    suspend fun seesTheNoteForRemovingOnlyThisDate() = seesText(getString(Res.string.tool_card_scope_this_remove_note))

    suspend fun seesTheNoteForRemovingThisAndFollowing() =
        seesText(getString(Res.string.tool_card_scope_following_remove_note))

    suspend fun doesNotSeeOnlyThisDate() {
        node(getString(Res.string.tool_card_scope_this)).assertDoesNotExist()
    }

    // ---- approving automatically ---------------------------------------------------------------

    suspend fun seesAutoApproveAddingOff() {
        autoApproveAdding().assertIsOff()
    }

    suspend fun seesAutoApproveAddingOn() {
        autoApproveAdding().assertIsOn()
    }

    suspend fun ticksAutoApproveAdding() {
        autoApproveAdding().performClick()
    }

    suspend fun ticksAutoApproveChanging() {
        node(getString(Res.string.tool_calendar_change_automatic_ask)).performClick()
    }

    suspend fun seesAddingIsNowAutomatic() = seesText(getString(Res.string.tool_calendar_add_now_automatic))

    suspend fun tapsUndo() {
        node(getString(Res.string.tool_automatic_undo)).performClick()
    }

    private suspend fun autoApproveAdding(): SemanticsNodeInteraction {
        val ask = getString(Res.string.tool_calendar_add_automatic_ask)
        waitForOne(hasText(ask))
        return node(ask)
    }

    // ---- a step that failed, and its fix -------------------------------------------------------

    suspend fun seesTheCalendarRefusedTheAdd() = seesText(getString(Res.string.tool_fix_calendar_rejected_title))

    /** What broke, then that nothing was added. */
    suspend fun seesWhatBrokeAndThatNothingWasAdded() =
        seesText(
            "${getString(Res.string.tool_fix_calendar_rejected_detail)} " +
                getString(Res.string.tool_fix_nothing_added),
        )

    suspend fun doesNotSeeTheFailedAddAsAStep() {
        test.onAllNodesWithText(getString(Res.string.tool_calendar_add_failed)).assertCountEquals(0)
    }

    suspend fun seesReconnectCalendar() = seesText(getString(Res.string.tool_fix_reconnect_calendar))

    suspend fun doesNotSeeReconnectCalendar() {
        test.onAllNodesWithText(getString(Res.string.tool_fix_reconnect_calendar)).assertCountEquals(0)
    }

    suspend fun tapsReconnectCalendar() {
        node(getString(Res.string.tool_fix_reconnect_calendar)).performClick()
    }

    /** The failed add among other steps lives in a fold named for it. */
    suspend fun opensTheFoldOfTheFailedAdd() {
        seesText(getString(Res.string.tool_calendar_add_failed)).performClick()
    }

    suspend fun seesTheFailedAddTimes(times: Int) {
        test.onAllNodesWithText(getString(Res.string.tool_calendar_add_failed)).assertCountEquals(times)
    }

    suspend fun seesTheCalendarIsConnected() = seesText(getString(Res.string.tool_fix_calendar_fixed_title))

    suspend fun tapsAskAgain() {
        test.onNodeWithContentDescription(getString(Res.string.tool_fix_ask_again)).performClick()
    }

    suspend fun doesNotSeeAskAgain() {
        test.onAllNodes(hasContentDescription(getString(Res.string.tool_fix_ask_again))).assertCountEquals(0)
    }

    // ---- editing a card before approving it ----------------------------------------------------

    suspend fun tapsEdit(scrolling: Boolean = false) {
        item(getString(Res.string.tool_card_edit), scrolling).performClick()
    }

    suspend fun tapsCancel() {
        node(getString(Res.string.tool_card_cancel)).performClick()
    }

    suspend fun tapsApproveWithChanges(scrolling: Boolean = false) {
        item(getString(Res.string.tool_card_approve_with_changes), scrolling).performClick()
    }

    suspend fun doesNotSeeApproveWithChanges() {
        node(getString(Res.string.tool_card_approve_with_changes)).assertDoesNotExist()
    }

    /** The title is typed, so it is a field holding [title]. */
    fun seesTheTitleField(title: String) {
        waitForOne(titleField(title))
        test.onNode(titleField(title)).assertIsDisplayed()
    }

    fun doesNotSeeAFieldHolding(text: String) {
        test.onNode(hasSetTextAction() and hasText(text)).assertDoesNotExist()
    }

    fun retitles(
        from: String,
        to: String,
    ) {
        test.onNode(titleField(from)).performTextReplacement(to)
    }

    private fun titleField(title: String) = hasSetTextAction() and hasText(title)

    suspend fun seesTheTitleIsMissing() = seesText(getString(Res.string.tool_card_field_empty))

    suspend fun doesNotSeeTheTitleIsMissing() {
        node(getString(Res.string.tool_card_field_empty)).assertDoesNotExist()
    }

    fun seesTheDay(day: String) {
        waitForOne(hasTestTag(DAY_FIELD_TAG))
        test.onNodeWithTag(DAY_FIELD_TAG).assertTextContains(day)
    }

    fun seesTheTime(time: String) {
        waitForOne(hasTestTag(TIME_FIELD_TAG))
        test.onNodeWithTag(TIME_FIELD_TAG).assertTextContains(time)
    }

    fun tapsTheDay() {
        test.onNodeWithTag(DAY_FIELD_TAG).performClick()
    }

    fun tapsTheTime() {
        test.onNodeWithTag(TIME_FIELD_TAG).performClick()
    }

    suspend fun seesTheDayMarkedChanged() =
        seesText(getString(Res.string.field_changed, getString(Res.string.tool_card_field_day)))

    suspend fun seesTheTimeMarkedChanged() =
        seesText(getString(Res.string.field_changed, getString(Res.string.tool_card_field_time)))

    fun seesAPickerOpen() {
        waitForOne(hasTestTag(PICKER_CONFIRM_TAG))
        test.onNodeWithTag(PICKER_CONFIRM_TAG).assertIsDisplayed()
    }

    fun confirmsThePicker() {
        test.onNodeWithTag(PICKER_CONFIRM_TAG).performClick()
    }

    fun dismissesThePicker() {
        test.onNodeWithTag(PICKER_DISMISS_TAG).performClick()
    }

    // The clock and calendar pickers are Material's own, and so are the words they give the dial.

    fun picksHour(hour: Int) {
        test.onNodeWithContentDescription("$hour hours").performClick()
    }

    /** A tap on the dial moves on to minutes by itself; a test's click doesn't, so it asks. */
    fun movesOnToMinutes() {
        test.onNode(hasContentDescription("Select minutes", substring = true)).performClick()
    }

    fun picksMinutes(minutes: Int) {
        test.onNodeWithContentDescription("$minutes minutes").performClick()
    }

    /** The picker words its days in the machine's locale: "Sunday, 4 October 2026" or "Sunday, October 4, 2026". */
    fun picksDay(
        weekday: String,
        day: Int,
        month: String,
        year: Int,
    ) {
        val date = hasText("$day $month $year", substring = true) or hasText("$month $day, $year", substring = true)
        test.onNode(hasText(weekday, substring = true) and date).performClick()
    }

    // ---- repeat and ends, in the Edit view -----------------------------------------------------

    suspend fun seesRepeatWeeklySelected() {
        seesSelected(getString(Res.string.tool_card_repeat_weekly), selected = true, scrolling = true)
    }

    suspend fun doesNotSeeRepeatWeekly() {
        node(getString(Res.string.tool_card_repeat_weekly)).assertDoesNotExist()
    }

    suspend fun seesRepeatNoneSelected() {
        seesSelected(getString(Res.string.tool_card_repeat_none), selected = true, scrolling = true)
    }

    suspend fun doesNotSeeRepeatNone() {
        node(getString(Res.string.tool_card_repeat_none)).assertDoesNotExist()
    }

    suspend fun picksRepeatDaily() {
        item(getString(Res.string.tool_card_repeat_daily), scrolling = true).performClick()
    }

    suspend fun seesEndsOnDateSelected() {
        seesSelected(getString(Res.string.tool_card_ends_on_date), selected = true, scrolling = true)
    }

    suspend fun seesEndsNeverSelected() {
        seesSelected(getString(Res.string.tool_card_ends_never), selected = true, scrolling = true)
    }

    suspend fun doesNotSeeEndsNever() {
        node(getString(Res.string.tool_card_ends_never)).assertDoesNotExist()
    }

    suspend fun picksEndsAfter() {
        item(getString(Res.string.tool_card_ends_after), scrolling = true).performClick()
    }

    fun seesTheLastDate(date: String) {
        waitForOne(hasTestTag(LAST_DATE_FIELD_TAG))
        test.onNodeWithTag(LAST_DATE_FIELD_TAG).assertTextContains(date)
    }

    /** The caption counts the rule out: how many sessions, the last on [last]. */
    suspend fun seesTheSessionsCounted(
        sessions: Int,
        last: String,
    ) {
        seesText(getPluralString(Res.plurals.tool_card_repeat_sessions, sessions, sessions, last), scrolling = true)
    }

    suspend fun seesTimes(times: Int) {
        seesText(getPluralString(Res.plurals.tool_card_times, times, times), scrolling = true)
    }

    suspend fun seesTheLastOneLandingOn(date: String) {
        seesText(getString(Res.string.tool_card_repeat_last, date), scrolling = true)
    }

    suspend fun tapsOneMore() {
        test.onNodeWithContentDescription(getString(Res.string.tool_card_times_more)).performScrollTo().performClick()
    }

    suspend fun seesWeekdayOn(day: Weekday) {
        weekday(day).assertIsOn()
    }

    suspend fun seesWeekdayOff(day: Weekday) {
        weekday(day).assertIsOff()
    }

    suspend fun tapsWeekday(day: Weekday) {
        weekday(day).performScrollTo().performClick()
    }

    suspend fun seesNoDaysAreTicked() = seesText(getString(Res.string.tool_card_repeat_no_days), scrolling = true)

    private suspend fun weekday(day: Weekday): SemanticsNodeInteraction {
        val label = getString(day.label)
        waitForOne(hasContentDescription(label))
        return test.onNodeWithContentDescription(label)
    }

    // ---- finding things ------------------------------------------------------------------------

    private fun node(
        text: String,
        substring: Boolean = false,
        unmerged: Boolean = false,
    ) = test.onNodeWithText(text, substring = substring, useUnmergedTree = unmerged)

    /** A node of [text], scrolled into view when the card is longer than the screen. */
    private fun item(
        text: String,
        scrolling: Boolean,
    ): SemanticsNodeInteraction = if (scrolling) node(text).performScrollTo() else node(text)

    private fun seesText(
        text: String,
        substring: Boolean = false,
        unmerged: Boolean = false,
        scrolling: Boolean = false,
    ): SemanticsNodeInteraction {
        waitForOne(hasText(text, substring = substring), unmerged)
        val seen = node(text, substring, unmerged)
        if (scrolling) seen.performScrollTo()
        return seen.assertIsDisplayed()
    }

    private fun seesSelected(
        text: String,
        selected: Boolean,
        scrolling: Boolean = false,
    ) {
        waitForOne(hasText(text))
        val seen = item(text, scrolling)
        if (selected) seen.assertIsSelected() else seen.assertIsNotSelected()
    }

    private fun boundsOf(
        text: String,
        substring: Boolean = false,
        unmerged: Boolean = false,
    ): DpRect {
        waitForOne(hasText(text, substring = substring), unmerged)
        return node(text, substring, unmerged).getUnclippedBoundsInRoot()
    }

    private fun waitForOne(
        matcher: SemanticsMatcher,
        unmerged: Boolean = false,
    ) {
        if (unmerged) {
            waitForCount(matcher, 1)
        } else {
            test.waitUntilExactlyOneExists(matcher, timeoutMillis = WAIT_MILLIS)
        }
    }

    private fun waitForCount(
        matcher: SemanticsMatcher,
        count: Int,
    ) {
        test.waitUntil(timeoutMillis = WAIT_MILLIS) {
            test.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().size == count
        }
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
        const val SEPARATOR = " · "
    }
}

/** The days of the week a card's chips are named for. */
enum class Weekday(
    val label: StringResource,
) {
    Monday(Res.string.tool_card_weekday_mon),
    Tuesday(Res.string.tool_card_weekday_tue),
    Wednesday(Res.string.tool_card_weekday_wed),
    Thursday(Res.string.tool_card_weekday_thu),
    Friday(Res.string.tool_card_weekday_fri),
    Saturday(Res.string.tool_card_weekday_sat),
    Sunday(Res.string.tool_card_weekday_sun),
}

@OptIn(ExperimentalTestApi::class)
suspend fun ComposeUiTest.onConversation(block: suspend ConversationRobot.() -> Unit) {
    ConversationRobot(this).block()
}

/** The screen under test, with only the callbacks a given test cares about wired up. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.conversationScreen(
    state: ChatSessionUiState,
    darkTheme: Boolean = false,
    onSend: (String) -> Unit = {},
    onComposerTextChange: (String) -> Unit = {},
    onRetry: (String) -> Unit = {},
    onTryAgain: () -> Unit = {},
    onSelectAgent: (String) -> Unit = {},
    onRetryAgents: () -> Unit = {},
    onDecide: (String, Boolean, ProposalDetails?) -> Unit = { _, _, _ -> },
    onConnectCalendar: () -> Unit = {},
    onApproveAutomatically: (String, ProposalDetails?) -> Unit = { _, _ -> },
    onUndoAutomatic: () -> Unit = {},
    onLookedOver: () -> Unit = {},
    memoryAge: (Instant) -> MemoryAge? = { MemoryAge.Month(MARCH) },
    eventTime: (EventTime) -> LocalEventTime? = { localEventTime(it.startsAt, offsetSeconds = 0, it.allDay) },
) {
    setContent {
        StillTheme(darkTheme = darkTheme) {
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
                onLookedOver = onLookedOver,
                memoryAge = memoryAge,
                eventTime = eventTime,
                onBack = {},
                memberName = "Emma",
            )
        }
    }
}

/** Every stored memory in the screen's states was noted in March. */
private const val MARCH = 3
