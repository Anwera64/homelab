package com.homelab.household.presentation.pinentry

/** What happens once the hub accepts the PIN. */
sealed interface PinEntryEvent {
    data object SignedIn : PinEntryEvent
}
