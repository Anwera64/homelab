package com.homelab.household.data.mapper

import com.homelab.household.data.dto.CalendarCredentialReadDto
import com.homelab.household.domain.model.CalendarConnection
import com.homelab.household.domain.model.CalendarProvider

/** The hub names providers `apple_icloud`, `google_caldav` and `caldav`. */
object CalendarDataMapper {
    private const val APPLE = "apple_icloud"
    private const val GOOGLE = "google_caldav"
    private const val OTHER = "caldav"

    fun toWire(provider: CalendarProvider): String =
        when (provider) {
            CalendarProvider.APPLE -> APPLE
            CalendarProvider.GOOGLE -> GOOGLE
            CalendarProvider.OTHER -> OTHER
        }

    fun toDomain(dto: CalendarCredentialReadDto): CalendarConnection =
        CalendarConnection(
            provider =
                when (dto.provider) {
                    APPLE -> CalendarProvider.APPLE
                    GOOGLE -> CalendarProvider.GOOGLE
                    else -> CalendarProvider.OTHER
                },
            account = dto.username,
            server = dto.url,
            calendarName = dto.calendarName,
            connectedAt = dto.updatedAt,
            needsReconnect = dto.needsReconnect,
        )
}
