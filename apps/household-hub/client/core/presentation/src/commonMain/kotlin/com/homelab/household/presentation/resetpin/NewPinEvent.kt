package com.homelab.household.presentation.resetpin

/** What happens once the hub takes the new PIN: they are signed in with it. */
sealed interface NewPinEvent {
    data object SignedIn : NewPinEvent
}
