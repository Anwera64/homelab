package com.homelab.household.presentation.googlesignin

sealed interface GoogleSignInEvent {
    /** Open Google's sign-in [page] in the browser, and hand back whatever address it returns to. */
    data class OpenBrowser(
        val page: String,
    ) : GoogleSignInEvent

    /** The hub reached the calendar and kept it. */
    data object Connected : GoogleSignInEvent
}
