package com.homelab.household.presentation.googlesignin

import com.homelab.household.domain.model.CalendarSignInFailure

/** Where signing in with Google stands. The hub keeps the calendar itself; this is only the member's side of it. */
sealed interface GoogleSignInStatus {
    data object Idle : GoogleSignInStatus

    /** Asking the hub for Google's page. */
    data object Starting : GoogleSignInStatus

    /** Google's page is open in the browser. */
    data object InBrowser : GoogleSignInStatus

    /** The sign-in came back without a calendar, for [failure]. Nothing was saved. */
    data class Failed(
        val failure: CalendarSignInFailure,
    ) : GoogleSignInStatus

    /** Nothing answered at the hub itself. */
    data object HubUnreachable : GoogleSignInStatus

    /** The hub has no Google sign-in set up. */
    data object Unavailable : GoogleSignInStatus
}
