package com.homelab.household.data.network

import java.util.TimeZone
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The phone's UTC offset, which moves with daylight saving: the memory page counts days with it. */
class DeviceUtcOffsetTest {
    private val original = TimeZone.getDefault()

    @AfterTest
    fun restore() = TimeZone.setDefault(original)

    @Test
    fun `GIVEN Madrid WHEN the offset is read in summer and in winter THEN it follows daylight saving`() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Madrid"))

        // 15 July and 15 January 2026, noon UTC.
        assertEquals(2 * 3600, deviceUtcOffsetSeconds(1_784_116_800_000L))
        assertEquals(1 * 3600, deviceUtcOffsetSeconds(1_768_478_400_000L))
    }
}
