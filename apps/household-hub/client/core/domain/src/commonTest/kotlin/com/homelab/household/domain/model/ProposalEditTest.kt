package com.homelab.household.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Editing a card before approving (canvas: ToolEdit): which details open as fields, what they start
 * as, and the edited details that reach the hub when only some of them changed.
 */
class ProposalEditTest {
    private fun card(
        details: ProposalDetails,
        action: ToolAction? = ToolAction.Create,
        tool: String = HubTool.CALENDAR_WRITE,
    ) = AnswerPart.Proposal("c-1", tool, action, details)

    private fun event(
        start: EventMoment?,
        end: EventMoment? = null,
        allDay: Boolean = false,
        title: String? = "Dinner together",
    ) = ProposalDetails.CalendarEvent(title = title, start = start, end = end, allDay = allDay)

    private fun note(
        action: ToolAction = ToolAction.Create,
        content: String = "Milk",
    ) = card(ProposalDetails.Note("Groceries", content), action, HubTool.DOCUMENT_WRITER)

    private val dinner =
        card(
            event(
                start = EventMoment(2026, 9, 13, hour = 20, minute = 0, offset = "+02:00"),
                end = EventMoment(2026, 9, 13, hour = 22, minute = 0, offset = "+02:00"),
            ),
        )
    private val asProposed = proposedValues(dinner)

    @Test
    fun `GIVEN an event with a time WHEN edited THEN its title day and time open as fields`() {
        assertEquals(listOf(EditField.What, EditField.Day, EditField.Time), editableFields(dinner))
    }

    @Test
    fun `GIVEN an all-day event WHEN edited THEN it has no time field`() {
        val trip = card(event(start = EventMoment(2026, 10, 3), allDay = true, title = "Trip"))

        assertEquals(listOf(EditField.What, EditField.Day), editableFields(trip))
    }

    @Test
    fun `GIVEN a new note WHEN edited THEN its title and words open and an addition only its words`() {
        assertEquals(listOf(EditField.What, EditField.Words), editableFields(note()))
        assertEquals(listOf(EditField.Words), editableFields(note(ToolAction.Append)))
    }

    @Test
    fun `GIVEN a removal or a replacement or an unknown write WHEN asked THEN there is nothing to edit`() {
        assertEquals(emptyList(), editableFields(card(event(start = null), ToolAction.Delete)))
        assertEquals(emptyList(), editableFields(note(ToolAction.Replace)))
        assertEquals(emptyList(), editableFields(card(ProposalDetails.Other, tool = "something_new")))
    }

    @Test
    fun `GIVEN a proposal WHEN its fields open THEN they start as what was proposed`() {
        assertEquals(
            EditValues(title = "Dinner together", words = "", day = EventDate(2026, 9, 13), time = TimeOfDay(20, 0)),
            asProposed,
        )
        assertEquals(EditValues(title = "Groceries", words = "Milk", day = null, time = null), proposedValues(note()))
    }

    @Test
    fun `GIVEN dates across leap years and year ends WHEN counted in days and back THEN they are the same date`() {
        assertEquals(0L, EventDate(1970, 1, 1).toEpochDay())
        assertEquals(20_727L, EventDate(2026, 10, 1).toEpochDay())
        listOf(EventDate(2024, 2, 29), EventDate(2026, 12, 31), EventDate(2027, 1, 1), EventDate(2000, 3, 1)).forEach {
            assertEquals(it, EventDate.fromEpochDay(it.toEpochDay()))
        }
    }

    @Test
    fun `GIVEN only the time changed WHEN approved THEN the start moves and the end keeps the length and zone`() {
        val edit = editProposal(dinner, asProposed.copy(time = TimeOfDay(20, 30)))

        assertEquals(
            event(
                title = null,
                start = EventMoment(2026, 9, 13, hour = 20, minute = 30, offset = "+02:00"),
                end = EventMoment(2026, 9, 13, hour = 22, minute = 30, offset = "+02:00"),
            ),
            edit.edited,
        )
        assertEquals(setOf(EditField.Time), edit.changed)
        assertEquals(emptySet(), edit.invalid)
    }

    @Test
    fun `GIVEN a new day late in the evening WHEN approved THEN the end crosses midnight with it`() {
        val party =
            card(
                event(
                    start = EventMoment(2026, 9, 13, hour = 22, minute = 0),
                    end = EventMoment(2026, 9, 14, hour = 1, minute = 0),
                    title = "Party",
                ),
            )

        val edit = editProposal(party, proposedValues(party).copy(day = EventDate(2026, 9, 30)))

        assertEquals(
            event(
                title = null,
                start = EventMoment(2026, 9, 30, hour = 22, minute = 0),
                end = EventMoment(2026, 10, 1, hour = 1, minute = 0),
            ),
            edit.edited,
        )
    }

    @Test
    fun `GIVEN a day in the next year WHEN approved THEN the start is in that year`() {
        val nye = card(event(start = EventMoment(2026, 12, 31, hour = 20, minute = 0), title = "Party"))

        val edit = editProposal(nye, proposedValues(nye).copy(day = EventDate(2027, 1, 2)))

        assertEquals(
            EventMoment(2027, 1, 2, hour = 20, minute = 0),
            (edit.edited as ProposalDetails.CalendarEvent).start,
        )
    }

    @Test
    fun `GIVEN an all-day event moved WHEN approved THEN it stays a whole day`() {
        val trip = card(event(start = EventMoment(2026, 10, 3), allDay = true, title = "Trip"))

        val edit = editProposal(trip, proposedValues(trip).copy(day = EventDate(2026, 10, 4)))

        assertEquals(event(title = null, start = EventMoment(2026, 10, 4), allDay = true), edit.edited)
    }

    @Test
    fun `GIVEN nothing changed WHEN approved THEN there is no edit`() {
        val edit = editProposal(dinner, asProposed.copy(title = " Dinner together "))

        assertNull(edit.edited)
        assertEquals(emptySet(), edit.changed)
        assertEquals(emptySet(), edit.invalid)
    }

    @Test
    fun `GIVEN a title and words changed on a note WHEN approved THEN both are sent as typed`() {
        val edit = editProposal(note(), proposedValues(note()).copy(title = "Shopping", words = "Milk\nEggs"))

        assertEquals(ProposalDetails.Note("Shopping", "Milk\nEggs"), edit.edited)
        assertEquals(setOf(EditField.What, EditField.Words), edit.changed)
    }

    @Test
    fun `GIVEN a title left empty WHEN approved THEN it is marked and there is no edit`() {
        val edit = editProposal(dinner, asProposed.copy(title = " ", time = TimeOfDay(21, 0)))

        assertEquals(setOf(EditField.What), edit.invalid)
        assertEquals(setOf(EditField.What, EditField.Time), edit.changed)
        assertNull(edit.edited)
    }

    @Test
    fun `GIVEN an edit WHEN laid over the proposal THEN what changed replaces it and the rest stays`() {
        val edited = event(title = null, start = EventMoment(2026, 9, 13, hour = 20, minute = 30, offset = "+02:00"))

        assertEquals(
            event(
                start = EventMoment(2026, 9, 13, hour = 20, minute = 30, offset = "+02:00"),
                end = EventMoment(2026, 9, 13, hour = 22, minute = 0, offset = "+02:00"),
            ),
            dinner.details.withEdit(edited),
        )
        assertEquals(
            ProposalDetails.Note("Groceries", "Eggs"),
            note().details.withEdit(ProposalDetails.Note(null, "Eggs")),
        )
    }
}
