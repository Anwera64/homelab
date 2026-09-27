package com.homelab.household.presentation.profile

/** Where disconnecting your calendar has got to. Nothing changes on the row until the hub agrees. */
enum class CalendarDisconnect {
    Idle,
    Disconnecting,

    /** The hub couldn't be reached, so the calendar is still connected. */
    Unreachable,

    /** The hub answered, but not with yes. The calendar is still connected. */
    Failed,
}
