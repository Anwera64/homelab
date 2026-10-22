package com.homelab.household.domain.repository

import com.homelab.household.domain.model.CalendarConnection
import com.homelab.household.domain.model.CalendarProvider
import com.homelab.household.domain.model.CalendarSource

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
    ): CalendarConnection

    suspend fun removeCalendar()

    /** Every calendar of the connected account. Throws `NotFoundException` when none is connected. */
    suspend fun listCalendars(): List<CalendarSource>

    /** Agents read only the calendars with these keys. Returns the list as the hub keeps it. */
    suspend fun pickCalendars(keys: List<String>): List<CalendarSource>

    /**
     * Google's sign-in page for this member. The hub keeps the whole sign-in and sends the browser
     * back to the app when it ends. Throws `GoogleSignInUnavailableException` when the hub has no
     * Google sign-in set up.
     */
    suspend fun startGoogleSignIn(): String
}
