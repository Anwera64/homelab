package com.homelab.household.presentation.pinapprove

/** Where approving a PIN reset stands. */
sealed interface PinApproveStatus {
    data object Idle : PinApproveStatus

    data object Checking : PinApproveStatus

    /** Your own PIN was wrong; it counts toward your own lockout, so the tries left are shown. */
    data class WrongPin(
        val attemptsLeft: Int,
    ) : PinApproveStatus

    data class Locked(
        val secondsLeft: Int,
    ) : PinApproveStatus

    /** Done: read this out to them. It works once. */
    data class Approved(
        val code: String,
    ) : PinApproveStatus

    data object Unreachable : PinApproveStatus

    data object Failed : PinApproveStatus
}
