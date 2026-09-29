package com.homelab.household.app.screens.conversation

import com.homelab.household.domain.model.AnswerPart
import com.homelab.household.domain.model.EventMoment
import com.homelab.household.domain.model.ProposalDetails
import com.homelab.household.domain.model.ToolAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What an approval card shows about the write it asks for (canvas: ToolApproveRemove): "Print shop",
 * "Fri 11 Sep, 18:00". It reads the details the data layer already made sense of; what the model
 * left out, the card leaves out.
 */
class ApprovalCardDetailsTest {
    private fun card(
        action: ToolAction?,
        details: ProposalDetails,
        tool: String = "calendar_write",
    ) = AnswerPart.Proposal("c-1", tool, action, details)

    @Test
    fun `GIVEN an event with a time WHEN read THEN it shows its title and its weekday and date and time`() {
        val details =
            approvalCardDetails(
                card(
                    ToolAction.Delete,
                    ProposalDetails.CalendarEvent(
                        title = "Print shop",
                        start = EventMoment(2026, 9, 11, hour = 18, minute = 0, offset = "+02:00"),
                        end = null,
                        allDay = false,
                    ),
                ),
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
                    ProposalDetails.CalendarEvent(
                        null,
                        EventMoment(2026, 10, 3, hour = 0, minute = 0),
                        null,
                        allDay = true,
                    ),
                ),
            )

        assertEquals(CardWhen(weekday = 5, day = 3, month = 10, time = null), details.whenAt)
        assertNull(details.title)
    }

    @Test
    fun `GIVEN a morning time WHEN read THEN it is written with two digits`() {
        val details =
            approvalCardDetails(
                card(
                    ToolAction.Create,
                    ProposalDetails.CalendarEvent(null, EventMoment(2026, 10, 3, 9, 5), null, false),
                ),
            )

        assertEquals("09:05", details.whenAt?.time)
    }

    @Test
    fun `GIVEN an event without a time it could read WHEN read THEN no time is shown`() {
        val details =
            approvalCardDetails(card(ToolAction.Update, ProposalDetails.CalendarEvent("Dentist", null, null, false)))

        assertEquals(ApprovalCardDetails("Dentist"), details)
    }

    @Test
    fun `GIVEN a note WHEN read THEN it shows the first line of its words`() {
        val details =
            approvalCardDetails(
                card(
                    ToolAction.Replace,
                    ProposalDetails.Note("Shopping", "\nMilk, eggs\nBread"),
                    tool = "document_writer",
                ),
            )

        assertEquals(ApprovalCardDetails("Shopping", preview = "Milk, eggs"), details)
    }

    @Test
    fun `GIVEN a write the phone has no words for WHEN read THEN the card shows no details`() {
        assertEquals(
            ApprovalCardDetails(title = null),
            approvalCardDetails(card(ToolAction.Create, ProposalDetails.Other)),
        )
    }

    @Test
    fun `GIVEN each action WHEN asked THEN only removing and replacing ask in the destructive style`() {
        assertEquals(CardAsk.Approve, cardAsk(ToolAction.Create))
        assertEquals(CardAsk.Approve, cardAsk(null))
        assertEquals(CardAsk.Remove, cardAsk(ToolAction.Delete))
        assertEquals(CardAsk.Replace, cardAsk(ToolAction.Replace))
    }
}
