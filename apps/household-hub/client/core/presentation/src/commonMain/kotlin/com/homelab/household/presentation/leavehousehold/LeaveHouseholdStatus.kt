package com.homelab.household.presentation.leavehousehold

/** Where leaving the household stands. */
sealed interface LeaveHouseholdStatus {
    data object Idle : LeaveHouseholdStatus

    data object Leaving : LeaveHouseholdStatus

    data object NotSixDigits : LeaveHouseholdStatus

    data class WrongPin(
        val attemptsLeft: Int,
    ) : LeaveHouseholdStatus

    data class Locked(
        val secondsLeft: Int,
    ) : LeaveHouseholdStatus

    /** Nothing can promote anyone, so the household cannot lose its only admin. */
    data object SoleAdmin : LeaveHouseholdStatus

    data object Unreachable : LeaveHouseholdStatus

    data object Failed : LeaveHouseholdStatus
}
