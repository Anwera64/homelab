package com.homelab.household.presentation.firstrun

/** Why creating the household didn't work, for the screen to put in the user's language. */
sealed interface FirstRunFailure {
    /** Nothing answered at the hub. */
    data object Unreachable : FirstRunFailure

    /** Someone else set the hub up first; there is nothing to create any more. */
    data object AlreadySetUp : FirstRunFailure

    data object Unknown : FirstRunFailure
}
