package com.homelab.household.presentation.calendarconnect

/** Where connecting a calendar stands. Each failure is a different fix, so each is said differently. */
sealed interface CalendarConnectStatus {
    data object Idle : CalendarConnectStatus

    /** The hub is trying the details against the calendar. Nothing is saved until it works. */
    data object Checking : CalendarConnectStatus

    /** The calendar answered and refused the account or password. */
    data object Rejected : CalendarConnectStatus

    /** The hub answered, but it could not reach the calendar's server. */
    data object CalendarUnreachable : CalendarConnectStatus

    /** Nothing answered at the hub itself. */
    data object HubUnreachable : CalendarConnectStatus

    data object Failed : CalendarConnectStatus
}
