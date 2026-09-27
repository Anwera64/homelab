package com.homelab.household.domain.model

/** Where a member's calendar lives. The hub reaches every one of them over CalDAV. */
enum class CalendarProvider {
    APPLE,
    GOOGLE,

    /** Nextcloud, Fastmail, a self-hosted server: the member types the address. */
    OTHER,
    ;

    /**
     * Google refuses every password over CalDAV, so it is connected by signing in with Google in a
     * browser. The hub keeps that sign-in; the others connect with a password.
     */
    val signsIn: Boolean get() = this == GOOGLE

    /** The server this provider always uses, or null when the member has to say. Google has none to ask for. */
    fun presetServer(): String? =
        when (this) {
            APPLE -> APPLE_SERVER
            GOOGLE, OTHER -> null
        }

    companion object {
        const val APPLE_SERVER = "https://caldav.icloud.com"
    }
}
