package com.homelab.household.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun `GIVEN an event with a time WHEN edited THEN its title day and time open as fields and then how it repeats`() {
        assertEquals(
            listOf(EditField.What, EditField.Day, EditField.Time, EditField.Repeat, EditField.Ends),
            editableFields(dinner),
        )
    }

    @Test
    fun `GIVEN an all-day event WHEN edited THEN it has no time field`() {
        val trip = card(event(start = EventMoment(2026, 10, 3), allDay = true, title = "Trip"))

        assertEquals(listOf(EditField.What, EditField.Day, EditField.Repeat, EditField.Ends), editableFields(trip))
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

    // ---- Repeat and Ends (canvas: RepeatEdit, RepeatEndsPicker, RepeatEndsAfter) ----

    private val gymStart = EventMoment(2026, 9, 29, hour = 7, minute = 0)
    private val tueThu = EventRepeat(RepeatEvery.Week, weekdays = listOf(1, 3), until = EventMoment(2026, 12, 24))

    private fun gym(
        action: ToolAction,
        repeat: EventRepeat? = tueThu,
        scope: EventScope? = null,
    ) = card(ProposalDetails.CalendarEvent("Gym", gymStart, null, false, repeat = repeat, scope = scope), action)

    private val withRepeat = listOf(EditField.What, EditField.Day, EditField.Time, EditField.Repeat, EditField.Ends)

    @Test
    fun `GIVEN an add or a change to a one-off WHEN edited THEN Repeat and Ends open and None is offered`() {
        listOf(
            gym(ToolAction.Create),
            gym(ToolAction.Create, repeat = null),
            gym(ToolAction.Update, repeat = null),
        ).forEach {
            assertEquals(withRepeat, editableFields(it))
            assertTrue(offersNoRepeat(it))
        }
    }

    @Test
    fun `GIVEN a change to a whole series or to this and following WHEN edited THEN Repeat and Ends open without None`() {
        val wholeSeries = gym(ToolAction.Update)
        val onwards = gym(ToolAction.Update, scope = EventScope.ThisAndFollowing)

        listOf(wholeSeries, onwards).forEach {
            assertEquals(withRepeat, editableFields(it))
            assertFalse(offersNoRepeat(it))
        }
    }

    @Test
    fun `GIVEN a change to one date of a series WHEN edited THEN it has no Repeat until the switch says this and following`() {
        val oneDate = gym(ToolAction.Update, scope = EventScope.OnlyThis)

        assertEquals(listOf(EditField.What, EditField.Day, EditField.Time), editableFields(oneDate))
        assertEquals(withRepeat, editableFields(oneDate, scope = EventScope.ThisAndFollowing))
    }

    @Test
    fun `GIVEN a rule the fields cannot show WHEN edited THEN Repeat and Ends stay closed`() {
        val yearly = gym(ToolAction.Create, repeat = EventRepeat(RepeatEvery.Year))
        val fortnightly =
            gym(ToolAction.Create, repeat = EventRepeat(RepeatEvery.Week, interval = 2, weekdays = listOf(1)))

        assertEquals(listOf(EditField.What, EditField.Day, EditField.Time), editableFields(yearly))
        assertEquals(listOf(EditField.What, EditField.Day, EditField.Time), editableFields(fortnightly))
    }

    @Test
    fun `GIVEN a repeating add WHEN its fields open THEN Repeat and Ends start as the rule says`() {
        val values = proposedValues(gym(ToolAction.Create))

        assertEquals(RepeatEvery.Week, values.repeat)
        assertEquals(setOf(1, 3), values.weekdays)
        assertEquals(RepeatEnd.OnDate, values.ends)
        assertEquals(EventDate(2026, 12, 24), values.lastDate)
        assertEquals(
            RepeatEnd.After to 10,
            proposedValues(gym(ToolAction.Create, repeat = EventRepeat(RepeatEvery.Day, count = 10))).let {
                it.ends to
                    it.times
            },
        )
        assertEquals(null to RepeatEnd.Never, proposedValues(dinner).let { it.repeat to it.ends })
    }

    @Test
    fun `GIVEN a one-off add WHEN Weekly is picked THEN the start's own weekday is ticked`() {
        val values = proposedValues(gym(ToolAction.Create, repeat = null)).pickRepeat(RepeatEvery.Week)

        assertEquals(RepeatEvery.Week, values.repeat)
        assertEquals(setOf(1), values.weekdays)
    }

    @Test
    fun `GIVEN a one-off add WHEN made weekly on Tue and Thu until 24 Dec THEN that rule is the repeat change`() {
        val add = gym(ToolAction.Create, repeat = null)
        val values =
            proposedValues(add).copy(
                repeat = RepeatEvery.Week,
                weekdays = setOf(3, 1),
                ends = RepeatEnd.OnDate,
                lastDate = EventDate(2026, 12, 24),
            )

        val edit = editProposal(add, values)

        assertEquals(setOf(EditField.Repeat, EditField.Ends), edit.changed)
        assertEquals(RepeatChange.To(tueThu), (edit.edited as ProposalDetails.CalendarEvent).repeatChange)
    }

    @Test
    fun `GIVEN a repeating add WHEN set to end after ten times or made a one-off THEN that is the repeat change`() {
        val add = gym(ToolAction.Create)

        val tenTimes = editProposal(add, proposedValues(add).copy(ends = RepeatEnd.After, times = 10))
        val oneOff = editProposal(add, proposedValues(add).copy(repeat = null))

        assertEquals(setOf(EditField.Ends), tenTimes.changed)
        assertEquals(
            RepeatChange.To(EventRepeat(RepeatEvery.Week, weekdays = listOf(1, 3), count = 10)),
            (tenTimes.edited as ProposalDetails.CalendarEvent).repeatChange,
        )
        assertEquals(RepeatChange.Stop, (oneOff.edited as ProposalDetails.CalendarEvent).repeatChange)
    }

    @Test
    fun `GIVEN a repeating add WHEN only its title is edited THEN no repeat change is sent`() {
        val add = gym(ToolAction.Create)

        val edit = editProposal(add, proposedValues(add).copy(title = "Swim"))

        assertEquals(setOf(EditField.What), edit.changed)
        assertNull((edit.edited as ProposalDetails.CalendarEvent).repeatChange)
    }

    @Test
    fun `GIVEN a repeat change WHEN laid over the proposal THEN the card shows the new rule`() {
        val proposal = gym(ToolAction.Create).details
        val daily = EventRepeat(RepeatEvery.Day)

        assertEquals(
            daily,
            (
                proposal.withEdit(
                    event(null).copy(repeatChange = RepeatChange.To(daily)),
                ) as ProposalDetails.CalendarEvent
            ).repeat,
        )
        assertNull(
            (
                proposal.withEdit(
                    event(null).copy(repeatChange = RepeatChange.Stop),
                ) as ProposalDetails.CalendarEvent
            ).repeat,
        )
    }

    @Test
    fun `GIVEN one date of a series switched to this and following WHEN its end is changed THEN the change is kept`() {
        val oneDate = gym(ToolAction.Update, scope = EventScope.OnlyThis)
        val values = proposedValues(oneDate).copy(ends = RepeatEnd.After, times = 4)

        val edit = editProposal(oneDate, values, scope = EventScope.ThisAndFollowing)

        assertEquals(setOf(EditField.Ends), edit.changed)
        assertEquals(setOf(), editProposal(oneDate, values).changed)
    }

    @Test
    fun `GIVEN Weekly with no day ticked WHEN approved THEN Repeat is invalid and nothing is sent`() {
        val add = gym(ToolAction.Create)

        val edit = editProposal(add, proposedValues(add).copy(weekdays = emptySet()))

        assertEquals(setOf(EditField.Repeat), edit.invalid)
        assertNull(edit.edited)
    }

    @Test
    fun `GIVEN an end before the start or no date or a count out of range WHEN approved THEN Ends is invalid`() {
        val add = gym(ToolAction.Create)
        val asIs = proposedValues(add)

        listOf(
            asIs.copy(lastDate = EventDate(2026, 9, 28)),
            asIs.copy(lastDate = null),
            asIs.copy(ends = RepeatEnd.After, times = 0),
            asIs.copy(ends = RepeatEnd.After, times = 100),
            asIs.copy(ends = RepeatEnd.After, times = null),
        ).forEach {
            assertEquals(setOf(EditField.Ends), editProposal(add, it).invalid, it.toString())
        }
        assertEquals(setOf(), editProposal(add, asIs.copy(lastDate = EventDate(2026, 9, 29))).invalid)
    }

    @Test
    fun `GIVEN a one-off WHEN Ends is left as it was THEN Repeat None needs no end`() {
        assertEquals(setOf(), editProposal(dinner, asProposed.copy(ends = RepeatEnd.OnDate)).invalid)
    }

    @Test
    fun `GIVEN After picked with no count yet WHEN picked THEN it starts at ten times`() {
        assertEquals(RepeatEnd.After to 10, asProposed.pickEnds(RepeatEnd.After).let { it.ends to it.times })
        assertEquals(4, asProposed.copy(times = 4).pickEnds(RepeatEnd.After).times)
    }

    @Test
    fun `GIVEN Tue and Thu from 29 Sep WHEN it ends on 24 Dec or after ten times THEN the caption counts 26 or lands on 29 Oct`() {
        val asIs = proposedValues(gym(ToolAction.Create))

        assertEquals(RepeatSummary(26, EventDate(2026, 12, 24)), repeatSummary(asIs))
        assertEquals(RepeatSummary(10, EventDate(2026, 10, 29)), repeatSummary(asIs.pickEnds(RepeatEnd.After)))
    }

    @Test
    fun `GIVEN daily and monthly rules WHEN counted THEN a monthly 31st skips the months without one`() {
        val asIs = proposedValues(gym(ToolAction.Create, repeat = null))

        assertEquals(
            RepeatSummary(7, EventDate(2026, 10, 5)),
            repeatSummary(asIs.copy(repeat = RepeatEvery.Day, ends = RepeatEnd.After, times = 7)),
        )
        val thirtyFirst =
            asIs.copy(
                day = EventDate(2026, 10, 31),
                repeat = RepeatEvery.Month,
                ends = RepeatEnd.After,
                times = 3,
            )
        assertEquals(RepeatSummary(3, EventDate(2027, 1, 31)), repeatSummary(thirtyFirst))
        assertEquals(
            RepeatSummary(2, EventDate(2026, 12, 31)),
            repeatSummary(thirtyFirst.copy(ends = RepeatEnd.OnDate, lastDate = EventDate(2027, 1, 30))),
        )
    }

    @Test
    fun `GIVEN no repeat or no end or no start WHEN counted THEN there is no caption`() {
        val asIs = proposedValues(gym(ToolAction.Create))

        assertNull(repeatSummary(asIs.copy(repeat = null)))
        assertNull(repeatSummary(asIs.copy(ends = RepeatEnd.Never)))
        assertNull(repeatSummary(asIs.copy(day = null)))
        assertNull(repeatSummary(asIs.copy(weekdays = emptySet())))
    }
}
