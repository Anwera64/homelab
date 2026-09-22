package com.homelab.household.presentation.resetpin

/** Where setting the new PIN stands. */
sealed interface NewPinStatus {
    data object Idle : NewPinStatus

    data object Setting : NewPinStatus

    /** Unknown, used or expired — the hub answers alike for all three. */
    data object Invalid : NewPinStatus

    data class Locked(
        val secondsLeft: Int,
    ) : NewPinStatus

    data object Unreachable : NewPinStatus

    data object Failed : NewPinStatus
}
