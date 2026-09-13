package com.homelab.household.presentation.launch

/**
 * Where launch sends the user once the hub has answered. A hub that can't be read sends nothing:
 * the user stays on launch with something to retry.
 *
 * There is no way home from here — a phone still signed in never opens on launch.
 */
sealed interface LaunchEvent {
    data object GoToSignIn : LaunchEvent
    data object GoToFirstRun : LaunchEvent
}
