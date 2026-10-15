package com.homelab.household.presentation.memories

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * When a memory was noted, as its row says it, counted in the phone's own days. "Now" is Thursday
 * 15 October 2026, 18:00 in Mexico City (UTC-6).
 */
@OptIn(ExperimentalTime::class)
class MemoryAgeTest {
    private val now = Instant.parse("2026-10-16T00:00:00Z")
    private val mexicoCity = -6 * 3600

    private fun age(at: Instant) = memoryAge(at, now, mexicoCity)

    @Test
    fun `GIVEN earlier the same local day WHEN aged THEN it is today`() {
        assertEquals(MemoryAge.Today, age(Instant.parse("2026-10-15T06:00:00Z")))
    }

    @Test
    fun `GIVEN a time that is already tomorrow in UTC but today locally WHEN aged THEN it is still today`() {
        assertEquals(MemoryAge.Today, age(now - 1.hours))
    }

    @Test
    fun `GIVEN the day before WHEN aged THEN it is yesterday`() {
        assertEquals(MemoryAge.Yesterday, age(Instant.parse("2026-10-14T20:00:00Z")))
    }

    @Test
    fun `GIVEN two to six days ago WHEN aged THEN it is the weekday's name`() {
        // Sunday 11 October, Friday 9 October.
        assertEquals(MemoryAge.Weekday(7), age(Instant.parse("2026-10-11T15:00:00Z")))
        assertEquals(MemoryAge.Weekday(5), age(Instant.parse("2026-10-09T15:00:00Z")))
    }

    @Test
    fun `GIVEN seven to thirteen days ago WHEN aged THEN it is last week`() {
        assertEquals(MemoryAge.LastWeek, age(Instant.parse("2026-10-08T15:00:00Z")))
        assertEquals(MemoryAge.LastWeek, age(Instant.parse("2026-10-02T15:00:00Z")))
    }

    @Test
    fun `GIVEN older this year WHEN aged THEN it is the month`() {
        assertEquals(MemoryAge.Month(month = 3), age(Instant.parse("2026-03-20T15:00:00Z")))
        assertEquals(MemoryAge.Month(month = 10), age(Instant.parse("2026-10-01T15:00:00Z")))
    }

    @Test
    fun `GIVEN an earlier year WHEN aged THEN it is the month and year`() {
        assertEquals(MemoryAge.MonthYear(month = 12, year = 2025), age(Instant.parse("2025-12-31T15:00:00Z")))
    }

    @Test
    fun `GIVEN a time in the future WHEN aged THEN it is today rather than nonsense`() {
        assertEquals(MemoryAge.Today, age(now + 3.days))
    }

    @Test
    fun `GIVEN an expiry WHEN its day is read THEN it is the local day it falls on`() {
        // 02:00 UTC on the 18th is still the 17th in Mexico City.
        assertEquals(LocalDay(day = 17, month = 10), localDay(Instant.parse("2026-10-18T02:00:00Z"), mexicoCity))
    }
}
