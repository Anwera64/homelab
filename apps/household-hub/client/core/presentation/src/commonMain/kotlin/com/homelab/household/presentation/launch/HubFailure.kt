package com.homelab.household.presentation.launch

/**
 * Why the hub couldn't be read, as something the UI can put into the user's own language.
 *
 * Deliberately not a message: the sentences the data layer and the network produce are English
 * and technical, and a screen can't translate them.
 */
sealed interface HubFailure {
    /** Nothing answered — the hub is off, or the phone is away from home without Tailscale. */
    data object NoRoute : HubFailure

    /** Nothing is at that address — the hub host is probably wrong. */
    data object AddressNotFound : HubFailure

    /** The hub answered, with a status that isn't a success. */
    data class Upstream(
        val statusCode: Int,
    ) : HubFailure

    /** Something answered, but not with JSON — a captive portal or the wrong server, most likely. */
    data class NotJson(
        val contentType: String,
    ) : HubFailure

    data object Unknown : HubFailure
}
