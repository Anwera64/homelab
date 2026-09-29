package com.homelab.household.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A moment on a card, as the model wrote it: the day of the week it falls on, and whether it is a whole day. */
class EventMomentTest {
    @Test
    fun `GIVEN dates across leap years and January WHEN asked THEN the weekday is right with Monday as 0`() {
        assertEquals(1, EventMoment(2000, 2, 29).weekday)
        assertEquals(0, EventMoment(2024, 1, 1).weekday)
        assertEquals(4, EventMoment(2026, 9, 11, hour = 18, minute = 0).weekday)
        assertEquals(6, EventMoment(2026, 10, 4).weekday)
    }

    @Test
    fun `GIVEN a moment without a time WHEN asked THEN it is a whole day`() {
        assertTrue(EventMoment(2026, 10, 3).isWholeDay)
        assertFalse(EventMoment(2026, 10, 3, hour = 21, minute = 0).isWholeDay)
    }
}
