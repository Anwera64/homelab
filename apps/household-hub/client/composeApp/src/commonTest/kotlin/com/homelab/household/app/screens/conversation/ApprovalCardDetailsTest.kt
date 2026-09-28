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
}
