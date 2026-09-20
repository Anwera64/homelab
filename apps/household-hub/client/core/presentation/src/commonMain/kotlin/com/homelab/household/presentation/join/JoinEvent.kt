package com.homelab.household.presentation.join

/** What happens once the hub accepts the code and the chosen PIN. */
sealed interface JoinEvent {
    data object Joined : JoinEvent
}
