package com.homelab.household.presentation.leavehousehold

/** Leaving for good, confirmed with your own PIN. */
data class LeaveHouseholdUiState(
    val pin: String = "",
    val status: LeaveHouseholdStatus = LeaveHouseholdStatus.Idle
)
