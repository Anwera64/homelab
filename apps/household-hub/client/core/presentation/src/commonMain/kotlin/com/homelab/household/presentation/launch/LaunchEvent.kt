package com.homelab.household.presentation.launch

/**
 * Where launch sends the user once the hub has answered. An unreachable or failing hub sends
 * nothing: the user stays on launch with something to retry.
 */
sealed interface LaunchEvent {
    data object GoToSignIn : LaunchEvent
    data object GoToFirstRun : LaunchEvent
}
