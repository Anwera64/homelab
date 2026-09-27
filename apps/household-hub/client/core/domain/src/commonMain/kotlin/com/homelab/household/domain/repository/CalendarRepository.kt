package com.homelab.household.domain.repository

import com.homelab.household.domain.model.CalendarConnection
import com.homelab.household.domain.model.CalendarProvider

/** The member's own calendar connection. One per member: connecting another replaces it. */
interface CalendarRepository {
    /** Null when no calendar is connected. */
    suspend fun getCalendar(): CalendarConnection?

    /**
     * The hub tests the connection before it keeps anything, so this either returns the saved
     * connection or throws `CalendarRejectedException` / `CalendarUnreachableException`.
     */
    suspend fun connectCalendar(
        provider: CalendarProvider,
        server: String,
        account: String,
        password: String,
        calendarName: String?,
    ): CalendarConnection

    suspend fun removeCalendar()
}
