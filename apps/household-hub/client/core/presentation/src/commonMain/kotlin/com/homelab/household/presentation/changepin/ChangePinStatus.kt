package com.homelab.household.presentation.changepin

/** Where changing a PIN stands. */
sealed interface ChangePinStatus {
    data object Idle : ChangePinStatus
    data object Saving : ChangePinStatus
    data class Locked(val secondsLeft: Int) : ChangePinStatus
    data object Unreachable : ChangePinStatus
    data object Failed : ChangePinStatus
}
