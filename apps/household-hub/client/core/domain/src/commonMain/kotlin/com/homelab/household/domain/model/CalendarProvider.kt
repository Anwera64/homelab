package com.homelab.household.domain.model

/** Where a member's calendar lives. The hub reaches every one of them over CalDAV. */
enum class CalendarProvider {
    APPLE,
    GOOGLE,

    /** Nextcloud, Fastmail, a self-hosted server: the member types the address. */
    OTHER,
    ;

    /**
     * The server this provider always uses, or null when the member has to say. Google gives every
     * account its own address, so it is built from the account rather than asked for.
     */
    fun presetServer(account: String): String? =
        when (this) {
            APPLE -> APPLE_SERVER
            GOOGLE -> "$GOOGLE_SERVER_PREFIX${account.trim()}$GOOGLE_SERVER_SUFFIX"
            OTHER -> null
        }

    companion object {
        const val APPLE_SERVER = "https://caldav.icloud.com"
        const val GOOGLE_SERVER_PREFIX = "https://apidata.googleusercontent.com/caldav/v2/"
        const val GOOGLE_SERVER_SUFFIX = "/events"
    }
}
