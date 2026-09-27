package com.homelab.household.domain.model

/** Why the hub could not connect a Google calendar. The hub sends it back as `reason=`. */
enum class CalendarSignInFailure {
    /** The member said no, or unticked calendar access, on Google's page. */
    DENIED,

    /** The sign-in took too long, or came back to the hub mangled. */
    EXPIRED,

    /** Google's calendar refused the new sign-in. */
    REJECTED,

    /** The hub couldn't reach Google. */
    UNREACHABLE,

    FAILED,
}

/** How a Google sign-in in the browser ended. The hub has already kept the calendar when it is [Connected]. */
sealed interface CalendarSignInResult {
    data object Connected : CalendarSignInResult

    /** The browser was closed before the hub answered. Nothing to say; the member can just try again. */
    data object Cancelled : CalendarSignInResult

    data class Failed(
        val failure: CalendarSignInFailure,
    ) : CalendarSignInResult

    companion object {
        const val CALLBACK_SCHEME = "hyggehub"
        private const val CONNECTED = "$CALLBACK_SCHEME://calendar/connected"
        private const val FAILED = "$CALLBACK_SCHEME://calendar/failed"
        private const val REASON = "reason="

        /** Reads the address the hub sent the browser to, or null when the browser came back with none. */
        fun fromCallback(address: String?): CalendarSignInResult {
            if (address == null) return Cancelled
            if (address == CONNECTED) return Connected
            if (!address.startsWith(FAILED)) return Failed(CalendarSignInFailure.FAILED)

            val reason =
                address
                    .substringAfter('?', "")
                    .split('&')
                    .firstOrNull { it.startsWith(REASON) }
                    ?.removePrefix(REASON)
            val failure = CalendarSignInFailure.entries.firstOrNull { it.name.equals(reason, ignoreCase = true) }
            return Failed(failure ?: CalendarSignInFailure.FAILED)
        }
    }
}
