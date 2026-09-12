package com.homelab.household.presentation.firstrun

/** Where first run sends the user once the household exists. */
sealed interface FirstRunEvent {
    data object GoToHome : FirstRunEvent
}
