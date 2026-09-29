package com.homelab.household.domain.model

/**
 * The one calendar a member has connected. The password never comes back from the hub.
 *
 * [connectedAt] is when the hub last checked it could reach the calendar: an ISO instant with its zone.
 */
data class CalendarConnection(
    val provider: CalendarProvider,
    val account: String,
    val server: String,
    val calendarName: String,
    val connectedAt: String? = null,
    /** Google stopped accepting the sign-in, so agents can't use the calendar until the member signs in again. */
    val needsReconnect: Boolean = false,
)
