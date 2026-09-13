package com.homelab.household.presentation.launch

/**
 * What the launch screen draws while it reads `GET /auth/status`. A hub that answers usefully
 * isn't a status: launch moves on straight from [Checking].
 */
sealed interface HubStatus {
    data object Checking : HubStatus

    /** The hub couldn't be read. Every reason gets the offline screen, and is asked again by itself. */
    data class Unavailable(val reason: HubFailure) : HubStatus
}
