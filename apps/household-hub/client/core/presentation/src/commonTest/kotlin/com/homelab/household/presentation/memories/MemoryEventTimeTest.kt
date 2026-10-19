package com.homelab.household.presentation.memories

import com.homelab.household.domain.model.DeviceTimeZone
import com.homelab.household.domain.model.EventTime
import com.homelab.household.domain.model.MemoryItem
import com.homelab.household.domain.model.MemoryOrigin
import com.homelab.household.domain.model.MemoryScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * A memory the hub rebuilt from its event keeps the time in UTC, and the phone shows it in its own
 * zone (memory spec §7). Madrid is UTC+2 in October; Mexico City is UTC-6.
 */
@OptIn(ExperimentalTime::class)
class MemoryEventTimeTest {
    private val madrid = DeviceTimeZone { 2 * 3600 }
    private val mexicoCity = DeviceTimeZone { -6 * 3600 }
    private val clock =
        object : Clock {
            override fun now() = Instant.parse("2026-10-17T12:00:00Z")
        }

    private fun item(
        eventTime: EventTime? = null,
        movedFrom: Instant? = null,
    ) = MemoryItem(
        id = "dinner",
        content = "Dinner together",
        scope = MemoryScope.HOUSEHOLD,
        origin = MemoryOrigin.Reflection,
        createdAt = Instant.parse("2026-10-16T09:00:00Z"),
        expiresAt = Instant.parse("2026-10-19T19:30:00Z"),
        speakerId = "emma",
        speakerName = "Emma",
        isMine = true,
        agentName = "Planner",
        agentAvatar = "🗓",
        canRemove = true,
        eventTime = eventTime,
        movedFrom = movedFrom,
    )

    @Test
    fun `GIVEN a timed event memory WHEN its row is made THEN the time is the phone's local day and clock time`() {
        val row = memoryRowOf(item(EventTime(Instant.parse("2026-10-19T17:30:00Z"), allDay = false)), madrid, clock)

        assertEquals(LocalEventTime(isoDay = 1, day = 19, month = 10, time = LocalTime(19, 30)), row.eventTime)
    }

    @Test
    fun `GIVEN a zone behind UTC WHEN the event is early in the UTC day THEN it falls on the day before`() {
        val row = memoryRowOf(item(EventTime(Instant.parse("2026-10-19T03:00:00Z"), allDay = false)), mexicoCity, clock)

        assertEquals(LocalEventTime(isoDay = 7, day = 18, month = 10, time = LocalTime(21, 0)), row.eventTime)
    }

    @Test
    fun `GIVEN an all-day event memory WHEN its row is made THEN it is the same date in every zone and has no time`() {
        val event = EventTime(Instant.parse("2026-10-24T00:00:00Z"), allDay = true)

        assertEquals(
            LocalEventTime(isoDay = 6, day = 24, month = 10, time = null),
            memoryRowOf(item(event), mexicoCity, clock).eventTime,
        )
        assertEquals(
            LocalEventTime(isoDay = 6, day = 24, month = 10, time = null),
            memoryRowOf(item(event), madrid, clock).eventTime,
        )
    }

    @Test
    fun `GIVEN an event that moved WHEN its row is made THEN where it was is in the phone's time`() {
        val row =
            memoryRowOf(
                item(
                    EventTime(Instant.parse("2026-10-19T17:30:00Z"), allDay = false),
                    Instant.parse("2026-10-18T18:00:00Z"),
                ),
                madrid,
                clock,
            )

        assertEquals(LocalEventTime(isoDay = 7, day = 18, month = 10, time = LocalTime(20, 0)), row.movedFrom)
    }

    @Test
    fun `GIVEN a memory in the model's own words WHEN its row is made THEN no time is added`() {
        val row = memoryRowOf(item(), madrid, clock)

        assertNull(row.eventTime)
        assertNull(row.movedFrom)
    }
}
