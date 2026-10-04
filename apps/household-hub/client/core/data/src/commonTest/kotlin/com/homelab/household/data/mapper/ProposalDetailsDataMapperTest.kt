package com.homelab.household.data.mapper

import com.homelab.household.domain.model.EventMoment
import com.homelab.household.domain.model.EventRepeat
import com.homelab.household.domain.model.EventScope
import com.homelab.household.domain.model.ProposalDetails
import com.homelab.household.domain.model.RepeatChange
import com.homelab.household.domain.model.RepeatEvery
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

    @Test
    fun `GIVEN a weekly event WHEN read THEN it keeps its days and the date it ends`() {
        val details =
            ProposalDetailsDataMapper.fromJson(
                "calendar_write",
                json(
                    """{"title": "Gym", "start_time": "2026-09-29T07:00:00Z", "repeat": {"frequency": "weekly", "days": ["TU", "th"], "until": "2026-12-24"}}""",
                ),
            ) as ProposalDetails.CalendarEvent

        assertEquals(
            EventRepeat(RepeatEvery.Week, weekdays = listOf(1, 3), until = EventMoment(2026, 12, 24)),
            details.repeat,
        )
    }

    @Test
    fun `GIVEN a monthly event every other month WHEN read THEN it keeps how often and how many times`() {
        val details =
            ProposalDetailsDataMapper.fromJson(
                "calendar_write",
                json("""{"repeat": {"frequency": "MONTHLY", "interval": 2, "count": 6}}"""),
            ) as ProposalDetails.CalendarEvent

        assertEquals(EventRepeat(RepeatEvery.Month, interval = 2, count = 6), details.repeat)
    }

    @Test
    fun `GIVEN a repeat the card has no words for WHEN read THEN the event is read as not repeating`() {
        listOf(
            """{"repeat": {"frequency": "hourly"}}""",
            """{"repeat": {"frequency": "weekly", "days": ["XX"]}}""",
            """{"repeat": {"frequency": "daily", "interval": 0}}""",
            """{"repeat": "weekly"}""",
            """{"repeat": {"frequency": "none"}}""",
        ).forEach { arguments ->
            val details =
                ProposalDetailsDataMapper.fromJson(
                    "calendar_write",
                    json(arguments),
                ) as ProposalDetails.CalendarEvent
            assertNull(details.repeat, arguments)
        }
    }

    @Test
    fun `GIVEN a change to one date of a repeating event WHEN read THEN it is for that date unless it says the following ones`() {
        fun scopeOf(arguments: String) =
            (
                ProposalDetailsDataMapper.fromJson(
                    "calendar_write",
                    json(arguments),
                ) as ProposalDetails.CalendarEvent
            ).scope

        assertEquals(
            EventScope.OnlyThis,
            scopeOf("""{"action": "delete", "event_id": "gym", "occurrence_start": "2026-10-01T07:00:00Z"}"""),
        )
        assertEquals(
            EventScope.ThisAndFollowing,
            scopeOf(
                """{"action": "update", "event_id": "gym", "occurrence_start": "2026-10-06T07:00:00Z", "scope": "following"}""",
            ),
        )
    }

    @Test
    fun `GIVEN a write that is not about one date of a series WHEN read THEN it has no which-dates`() {
        fun scopeOf(arguments: String) =
            (
                ProposalDetailsDataMapper.fromJson(
                    "calendar_write",
                    json(arguments),
                ) as ProposalDetails.CalendarEvent
            ).scope

        assertNull(scopeOf("""{"action": "create", "title": "Gym", "repeat": {"frequency": "weekly"}}"""))
        assertNull(scopeOf("""{"action": "delete", "event_id": "dentist"}"""))
    }

    @Test
    fun `GIVEN a write for the whole series WHEN read THEN it has no which-dates even when it names a date`() {
        fun scopeOf(arguments: String) =
            (
                ProposalDetailsDataMapper.fromJson(
                    "calendar_write",
                    json(arguments),
                ) as ProposalDetails.CalendarEvent
            ).scope

        assertNull(scopeOf("""{"action": "delete", "event_id": "gym", "scope": "all"}"""))
        assertNull(
            scopeOf(
                """{"action": "delete", "event_id": "gym", "occurrence_start": "2026-10-14T14:00:00+02:00", "scope": "all"}""",
            ),
        )
    }

    @Test
    fun `GIVEN the member picked this and following on the card WHEN written back THEN the hub is told which dates`() {
        val picked =
            ProposalDetails.CalendarEvent(
                title = null,
                start = null,
                end = null,
                allDay = false,
                scope = EventScope.ThisAndFollowing,
            )

        assertEquals(json("\"following\""), ProposalDetailsDataMapper.toArguments(picked)["scope"])
        assertEquals(
            json("\"this\""),
            ProposalDetailsDataMapper.toArguments(picked.copy(scope = EventScope.OnlyThis))["scope"],
        )
    }

    @Test
    fun `GIVEN an event with a repeat but no which-dates WHEN written back THEN neither is sent and the hub keeps what the model asked`() {
        val arguments =
            ProposalDetailsDataMapper.toArguments(
                ProposalDetails.CalendarEvent(null, null, null, false, repeat = EventRepeat(RepeatEvery.Day)),
            )

        assertEquals(json("""{"is_all_day": false}"""), arguments)
    }

    @Test
    fun `GIVEN a new rule picked on the card WHEN written back THEN the hub gets the whole repeat`() {
        val weekly =
            ProposalDetails.CalendarEvent(
                null,
                null,
                null,
                false,
                repeatChange =
                    RepeatChange.To(
                        EventRepeat(RepeatEvery.Week, weekdays = listOf(1, 3), until = EventMoment(2026, 12, 24)),
                    ),
            )
        val tenDays = weekly.copy(repeatChange = RepeatChange.To(EventRepeat(RepeatEvery.Day, count = 10)))

        assertEquals(
            json("""{"frequency": "weekly", "interval": 1, "days": ["TU", "TH"], "until": "2026-12-24"}"""),
            ProposalDetailsDataMapper.toArguments(weekly)["repeat"],
        )
        assertEquals(
            json("""{"frequency": "daily", "interval": 1, "count": 10}"""),
            ProposalDetailsDataMapper.toArguments(tenDays)["repeat"],
        )
    }

    @Test
    fun `GIVEN the member made it a one-off WHEN written back THEN the hub is told it does not repeat`() {
        val oneOff = ProposalDetails.CalendarEvent(null, null, null, false, repeatChange = RepeatChange.Stop)

        assertEquals(json("""{"frequency": "none"}"""), ProposalDetailsDataMapper.toArguments(oneOff)["repeat"])
    }
}
