package com.homelab.household.presentation.removemember

/** What happens once they are gone. */
sealed interface RemoveMemberEvent {
    data object Removed : RemoveMemberEvent
}
