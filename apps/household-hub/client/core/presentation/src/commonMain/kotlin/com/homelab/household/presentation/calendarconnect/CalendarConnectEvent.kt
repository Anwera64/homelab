package com.homelab.household.presentation.calendarconnect

/** What happens once the hub has reached the calendar and kept it. */
sealed interface CalendarConnectEvent {
    data object Connected : CalendarConnectEvent
}
