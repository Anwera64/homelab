package com.homelab.household.data.mapper

import com.homelab.household.domain.model.EventMoment
import com.homelab.household.domain.model.ProposalDetails
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A write's details, read from the arguments the model asked with into what the phone shows on the
 * card, and written back when the member changes them. The hub's argument names stay in this mapper.
 */
class ProposalDetailsDataMapperTest {
    private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

    @Test
    fun `GIVEN an event with a time WHEN read THEN its title and moments are kept as written`() {
        val details =
            ProposalDetailsDataMapper.fromJson(
                "calendar_write",
                json(
                    """{"action": "create", "title": " Print shop ", "start_time": "2026-09-11T18:00:00+02:00", "end_time": "2026-09-11T19:30:00", "is_all_day": false, "reminder": 15}""",
                ),
            )

        assertEquals(
            ProposalDetails.CalendarEvent(
                title = "Print shop",
                start = EventMoment(2026, 9, 11, hour = 18, minute = 0, offset = "+02:00"),
                end = EventMoment(2026, 9, 11, hour = 19, minute = 30),
                allDay = false,
            ),
            details,
        )
    }

    @Test
    fun `GIVEN an all-day event WHEN read THEN its moments have no time`() {
        val details =
            ProposalDetailsDataMapper.fromJson(
                "calendar_write",
                json("""{"start_time": "2026-10-03T00:00:00", "is_all_day": true}"""),
            )

        assertEquals(ProposalDetails.CalendarEvent(null, EventMoment(2026, 10, 3), null, allDay = true), details)
    }

    @Test
    fun `GIVEN a date on its own WHEN read THEN it is a whole day`() {
        val details = ProposalDetailsDataMapper.fromJson("calendar_write", json("""{"start_time": "2026-10-03"}"""))

        assertEquals(EventMoment(2026, 10, 3), (details as ProposalDetails.CalendarEvent).start)
    }

    @Test
    fun `GIVEN times that are not timestamps and a blank title WHEN read THEN they are left out rather than guessed at`() {
        val details =
            ProposalDetailsDataMapper.fromJson(
                "calendar_write",
                json("""{"title": "  ", "start_time": "tomorrow at six", "end_time": "2026-13-01T10:00:00"}"""),
            )

        assertEquals(ProposalDetails.CalendarEvent(null, null, null, allDay = false), details)
    }

    @Test
    fun `GIVEN a note WHEN read THEN its title and words are kept`() {
        val details =
            ProposalDetailsDataMapper.fromJson(
                "document_writer",
                json("""{"action": "replace", "title": "Shopping", "content": "\nMilk, eggs\nBread"}"""),
            )

        assertEquals(ProposalDetails.Note(title = "Shopping", content = "\nMilk, eggs\nBread"), details)
    }

    @Test
    fun `GIVEN a tool this phone has no card words for or no arguments WHEN read THEN nothing is guessed`() {
        assertEquals(ProposalDetails.Other, ProposalDetailsDataMapper.fromJson("garden_water", json("""{"zone": 2}""")))
        assertEquals(
            ProposalDetails.CalendarEvent(null, null, null, false),
            ProposalDetailsDataMapper.fromJson("calendar_write", null),
        )
        assertEquals(
            ProposalDetails.Note(null, null),
            ProposalDetailsDataMapper.fromJson("document_writer", json("[]")),
        )
    }

    @Test
    fun `GIVEN an event changed on its card WHEN written back THEN it uses the hub's names and keeps the zone it had`() {
        val arguments =
            ProposalDetailsDataMapper.toArguments(
                ProposalDetails.CalendarEvent(
                    title = "Dinner",
                    start = EventMoment(2026, 10, 3, hour = 21, minute = 0, offset = "+02:00"),
                    end = EventMoment(2026, 10, 3, hour = 22, minute = 30),
                    allDay = false,
                ),
            )

        assertEquals(
            json(
                """{"title": "Dinner", "start_time": "2026-10-03T21:00:00+02:00", "end_time": "2026-10-03T22:30:00", "is_all_day": false}""",
            ),
            arguments,
        )
    }

    @Test
    fun `GIVEN an all-day event and a note changed on their cards WHEN written back THEN only what they have is sent`() {
        assertEquals(
            json("""{"start_time": "2026-10-03", "is_all_day": true}"""),
            ProposalDetailsDataMapper.toArguments(
                ProposalDetails.CalendarEvent(null, EventMoment(2026, 10, 3), null, true),
            ),
        )
        assertEquals(
            json("""{"title": "Shopping", "content": "Milk"}"""),
            ProposalDetailsDataMapper.toArguments(ProposalDetails.Note("Shopping", "Milk")),
        )
        assertEquals(JsonObject(emptyMap()), ProposalDetailsDataMapper.toArguments(ProposalDetails.Other))
    }
}
