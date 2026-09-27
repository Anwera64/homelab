package com.homelab.household.presentation.profile

import com.homelab.household.domain.model.CalendarProvider

/** The Calendar row on the profile: one calendar per member, or an offer to connect one. */
sealed interface CalendarRow {
    data object Loading : CalendarRow

    data object None : CalendarRow

    /** [minutesAgo] is how long since the hub last checked it could reach the calendar, when it said. */
    data class Connected(
        val provider: CalendarProvider,
        val account: String,
        val minutesAgo: Long?,
        /** Google stopped accepting the sign-in; the row offers to sign in again. */
        val needsSignInAgain: Boolean = false,
    ) : CalendarRow

    /** The hub couldn't say. The profile's own failure line already says why. */
    data object Unknown : CalendarRow
}
