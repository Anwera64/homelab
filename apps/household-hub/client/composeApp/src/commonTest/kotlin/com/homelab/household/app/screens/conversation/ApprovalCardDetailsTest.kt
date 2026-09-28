package com.homelab.household.app.screens.conversation

import com.homelab.household.domain.model.AnswerPart
import com.homelab.household.domain.model.ToolAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What an approval card shows, read from what the model asked for (canvas: ToolApproveRemove):
 * "Print shop", "Fri 11 Sep, 18:00". What it can't read it leaves out rather than guess at.
 */
class ApprovalCardDetailsTest {
    private fun card(
        action: ToolAction?,
        vararg arguments: Pair<String, Any?>,
        tool: String = "calendar_write",
    ) = AnswerPart.Proposal("c-1", tool, action, mapOf(*arguments))

    @Test
    fun `GIVEN an event with a time WHEN read THEN it shows its title and its weekday and date and time as written`() {
        val details =
            approvalCardDetails(
                card(ToolAction.Delete, "title" to "Print shop", "start_time" to "2026-09-11T18:00:00+02:00"),
            )

        assertEquals(
            ApprovalCardDetails("Print shop", CardWhen(weekday = 4, day = 11, month = 9, time = "18:00")),
            details,
        )
    }

    @Test
    fun `GIVEN an all-day event WHEN read THEN it has no time`() {
        val details =
            approvalCardDetails(
                card(
                    ToolAction.Create,
                    "start_time" to "2026-10-03T00:00:00",
                    "is_all_day" to true,
                ),
            )

        assertEquals(CardWhen(weekday = 5, day = 3, month = 10, time = null), details.whenAt)
        assertNull(details.title)
    }

    @Test
    fun `GIVEN dates across leap years and January WHEN read THEN the weekday is right`() {
        assertEquals(1, cardWhen("2000-02-29")?.weekday)
        assertEquals(0, cardWhen("2024-01-01")?.weekday)
        assertNull(cardWhen("2024-01-01")?.time)
    }

    @Test
    fun `GIVEN a time that is not a timestamp WHEN read THEN it is not shown`() {
        assertNull(cardWhen("tomorrow at six"))
        assertNull(cardWhen("2026-13-01"))
    }

    @Test
    fun `GIVEN a note WHEN read THEN it shows the first line of its words`() {
        val details =
            approvalCardDetails(
                card(
                    ToolAction.Replace,
                    "title" to "Shopping",
                    "content" to "\nMilk, eggs\nBread",
                    tool = "document_writer",
                ),
            )

        assertEquals(ApprovalCardDetails("Shopping", preview = "Milk, eggs"), details)
    }

    @Test
    fun `GIVEN each action WHEN asked THEN only removing and replacing ask in the destructive style`() {
        assertEquals(CardAsk.Approve, cardAsk(ToolAction.Create))
        assertEquals(CardAsk.Approve, cardAsk(null))
        assertEquals(CardAsk.Remove, cardAsk(ToolAction.Delete))
        assertEquals(CardAsk.Replace, cardAsk(ToolAction.Replace))
    }

    // ---- repeating events ---------------------------------------------------

    private val gym =
        mapOf("frequency" to "weekly", "interval" to 1L, "days" to listOf("TU", "TH"), "until" to "2026-12-24")

    @Test
    fun `GIVEN a weekly event on two days until a date WHEN read THEN the card knows its days and last date`() {
        val details = approvalCardDetails(card(ToolAction.Create, "title" to "Gym", "repeat" to gym))

        assertEquals(
            CardRepeat(
                every = RepeatEvery.Week,
                weekdays = listOf(1, 3),
                until = CardWhen(weekday = 3, day = 24, month = 12, time = null),
            ),
            details.repeat,
        )
        assertNull(details.scope, "a new event has no dates to choose between")
    }

    @Test
    fun `GIVEN every other month six times WHEN read THEN the card knows the gap and the count`() {
        val repeat =
            approvalCardDetails(
                card(
                    ToolAction.Create,
                    "repeat" to mapOf("frequency" to "monthly", "interval" to 2L, "count" to 6L),
                ),
            ).repeat

        assertEquals(CardRepeat(every = RepeatEvery.Month, interval = 2, count = 6), repeat)
    }

    @Test
    fun `GIVEN a repeat the card has no words for WHEN read THEN no repeat line is shown`() {
        assertNull(approvalCardDetails(card(ToolAction.Create, "repeat" to mapOf("frequency" to "hourly"))).repeat)
        assertNull(approvalCardDetails(card(ToolAction.Create, "repeat" to "weekly")).repeat)
        assertNull(
            approvalCardDetails(
                card(
                    ToolAction.Create,
                    "repeat" to mapOf("frequency" to "weekly", "days" to listOf("XX")),
                ),
            ).repeat,
        )
    }

    @Test
    fun `GIVEN one date of a series to remove WHEN read THEN the card asks which dates and starts on only this one unless the agent said otherwise`() {
        val one = card(ToolAction.Delete, "occurrence_start" to "2026-10-01T07:00:00Z", "repeat" to gym)
        val rest = card(ToolAction.Update, "occurrence_start" to "2026-10-06T07:00:00Z", "scope" to "following")

        assertEquals(CardScope.OnlyThis, approvalCardDetails(one).scope)
        assertEquals(CardScope.ThisAndFollowing, approvalCardDetails(rest).scope)
        assertNull(
            approvalCardDetails(card(ToolAction.Delete, "title" to "Dentist")).scope,
            "a one-off has no dates to choose between",
        )
    }

    @Test
    fun `GIVEN the member switched which dates WHEN approving THEN only the change is sent`() {
        assertEquals(
            mapOf("scope" to "following"),
            scopeChange(proposed = CardScope.OnlyThis, chosen = CardScope.ThisAndFollowing),
        )
        assertEquals(
            mapOf("scope" to "this"),
            scopeChange(proposed = CardScope.ThisAndFollowing, chosen = CardScope.OnlyThis),
        )
        assertNull(scopeChange(proposed = CardScope.OnlyThis, chosen = CardScope.OnlyThis))
        assertNull(scopeChange(proposed = null, chosen = null))
    }
}
