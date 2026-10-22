package com.homelab.household.data.mapper

import com.homelab.household.data.dto.CalendarCredentialReadDto
import com.homelab.household.data.dto.CalendarSourceDto
import com.homelab.household.domain.model.CalendarSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The hub's calendar, read into the phone's: its times always carry their zone once past this mapper. */
class CalendarDataMapperTest {
    private fun dto(updatedAt: String?) =
        CalendarCredentialReadDto(
            id = "c-1",
            provider = "apple_icloud",
            url = "https://caldav.icloud.com",
            username = "emma@icloud.com",
            updatedAt = updatedAt,
        )

    @Test
    fun `GIVEN a time the hub wrote without its zone WHEN read THEN it is marked as the UTC it is`() {
        assertEquals("2026-09-26T20:04:00Z", CalendarDataMapper.toDomain(dto("2026-09-26T20:04:00")).connectedAt)
    }

    @Test
    fun `GIVEN a time with its zone WHEN read THEN it is kept as written`() {
        assertEquals("2026-09-26T20:04:00Z", CalendarDataMapper.toDomain(dto("2026-09-26T20:04:00Z")).connectedAt)
        assertEquals(
            "2026-09-26T22:04:00+02:00",
            CalendarDataMapper.toDomain(dto("2026-09-26T22:04:00+02:00")).connectedAt,
        )
        assertEquals(
            "2026-09-26T15:04:00-05:00",
            CalendarDataMapper.toDomain(dto("2026-09-26T15:04:00-05:00")).connectedAt,
        )
    }

    @Test
    fun `GIVEN no time WHEN read THEN there is none`() {
        assertNull(CalendarDataMapper.toDomain(dto(null)).connectedAt)
    }

    @Test
    fun `GIVEN a read only calendar the member picked WHEN read THEN it is a picked source that cannot be written`() {
        val source =
            CalendarDataMapper.toDomain(
                CalendarSourceDto(key = "c2", name = "Birthdays", writable = false, picked = true),
            )

        assertEquals(CalendarSource(key = "c2", name = "Birthdays", writable = false, picked = true), source)
    }
}
