package com.homelab.household.presentation.changepin

/** What happens once the hub takes the new PIN: every other device has to sign in again. */
sealed interface ChangePinEvent {
    data object Changed : ChangePinEvent
}
