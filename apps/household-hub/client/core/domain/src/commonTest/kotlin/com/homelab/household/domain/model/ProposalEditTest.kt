package com.homelab.household.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Editing a card before approving (canvas: ToolEdit): which details open as fields, what the member
 * may type into them, and the edited details that reach the hub when only some of them changed.
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
    fun `GIVEN a day as the card writes it or as people type it WHEN read THEN it is that day and month`() {
        assertEquals(CardDay(13, 9), parseDay("Sat 13 Sep"))
        assertEquals(CardDay(13, 9), parseDay("13 september"))
        assertEquals(CardDay(3, 10), parseDay("Oct 3"))
        assertEquals(CardDay(3, 10), parseDay("3/10"))
        assertEquals(CardDay(3, 10), parseDay("2026-10-03"))
        assertNull(parseDay("Saturday"))
        assertNull(parseDay("31 Sep"))
        assertNull(parseDay("13 Smarch"))
    }

    @Test
    fun `GIVEN a time as people type it WHEN read THEN it is hours and minutes`() {
        assertEquals(TimeOfDay(20, 30), parseTime("20:30"))
        assertEquals(TimeOfDay(8, 30), parseTime("8.30"))
        assertEquals(TimeOfDay(20, 0), parseTime("20"))
        assertEquals(TimeOfDay(7, 5), parseTime("0705"))
        assertNull(parseTime("25:00"))
        assertNull(parseTime("20:61"))
        assertNull(parseTime("soon"))
    }

    @Test
    fun `GIVEN a proposal WHEN its fields open THEN the time reads as hours and minutes and the words as given`() {
        assertEquals("20:00", proposedText(dinner, EditField.Time))
        assertEquals("Dinner together", proposedText(dinner, EditField.What))
        assertEquals("Milk", proposedText(note(), EditField.Words))
    }

    @Test
    fun `GIVEN only the time changed WHEN approved THEN the start moves and the end keeps the length and zone`() {
        val edit = editProposal(dinner, mapOf(EditField.Time to "20:30"))

        assertEquals(
            event(
                title = null,
                start = EventMoment(2026, 9, 13, hour = 20, minute = 30, offset = "+02:00"),
                end = EventMoment(2026, 9, 13, hour = 22, minute = 30, offset = "+02:00"),
            ),
            edit.edited,
        )
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

        val edit = editProposal(party, mapOf(EditField.Day to "30 Sep"))

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
    fun `GIVEN a day in early January for a December event WHEN approved THEN it is the next year`() {
        val nye = card(event(start = EventMoment(2026, 12, 31, hour = 20, minute = 0), title = "Party"))

        val edit = editProposal(nye, mapOf(EditField.Day to "2 Jan"))

        assertEquals(
            EventMoment(2027, 1, 2, hour = 20, minute = 0),
            (edit.edited as ProposalDetails.CalendarEvent).start,
        )
    }

    @Test
    fun `GIVEN an all-day event moved WHEN approved THEN it stays a whole day`() {
        val trip = card(event(start = EventMoment(2026, 10, 3), allDay = true, title = "Trip"))

        val edit = editProposal(trip, mapOf(EditField.Day to "Sun 4 Oct"))

        assertEquals(event(title = null, start = EventMoment(2026, 10, 4), allDay = true), edit.edited)
    }

    @Test
    fun `GIVEN nothing changed WHEN approved THEN there is no edit`() {
        val edit =
            editProposal(
                dinner,
                mapOf(EditField.What to " Dinner together ", EditField.Day to "Sun 13 Sep", EditField.Time to "20:00"),
            )

        assertNull(edit.edited)
        assertEquals(emptySet(), edit.invalid)
    }

    @Test
    fun `GIVEN a title and words changed on a note WHEN approved THEN both are sent as typed`() {
        val edit = editProposal(note(), mapOf(EditField.What to "Shopping", EditField.Words to "Milk\nEggs"))

        assertEquals(ProposalDetails.Note("Shopping", "Milk\nEggs"), edit.edited)
    }

    @Test
    fun `GIVEN a field left empty or a day or time that can't be read WHEN approved THEN it is marked and there is no edit`() {
        val edit =
            editProposal(dinner, mapOf(EditField.What to " ", EditField.Day to "someday", EditField.Time to "late"))

        assertEquals(setOf(EditField.What, EditField.Day, EditField.Time), edit.invalid)
        assertNull(edit.edited)
    }

    @Test
    fun `GIVEN a field WHEN typed back to what was proposed THEN it no longer reads as changed`() {
        assertFalse(isChanged(dinner, EditField.Time, "20:00"))
        assertFalse(isChanged(dinner, EditField.Day, "13 Sep"))
        assertFalse(isChanged(dinner, EditField.What, "Dinner together "))
        assertTrue(isChanged(dinner, EditField.Time, "20:30"))
        assertTrue(isChanged(dinner, EditField.Day, "14 Sep"))
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
