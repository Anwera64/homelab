package com.homelab.household.presentation.profile

/** What happens once the phone forgets who was signed in. */
sealed interface ProfileEvent {
    data object SignedOut : ProfileEvent
}
