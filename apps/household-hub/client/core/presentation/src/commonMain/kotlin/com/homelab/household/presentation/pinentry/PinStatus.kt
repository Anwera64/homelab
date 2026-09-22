package com.homelab.household.presentation.pinentry

/** Where the PIN pad stands. */
sealed interface PinStatus {
    data object Idle : PinStatus

    /** Six digits are with the hub; the pad ignores input until it answers. */
    data object Checking : PinStatus

    /** The last PIN was wrong. Stays while the next one is typed, so the count stays in view. */
    data class WrongPin(
        val attemptsLeft: Int,
    ) : PinStatus

    /** Too many misses; the pad ignores input until [secondsLeft] runs out. */
    data class Locked(
        val secondsLeft: Int,
    ) : PinStatus

    data object Unreachable : PinStatus

    data object Failed : PinStatus
}
