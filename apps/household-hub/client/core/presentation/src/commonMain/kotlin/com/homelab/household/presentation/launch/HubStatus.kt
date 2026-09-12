package com.homelab.household.presentation.launch

/** What the launch screen learned from `GET /auth/status`. */
sealed interface HubStatus {
    data object Checking : HubStatus
    data class Ready(val memberCount: Int) : HubStatus
    data object FirstRun : HubStatus
    data object Unreachable : HubStatus
    data class Failed(val reason: HubFailure) : HubStatus
}
