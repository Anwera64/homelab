package com.homelab.household.presentation.launch

/** Everything the launch screen draws: the hub it is calling, and what that call found. */
data class LaunchUiState(
    val hubAddress: String = "",
    val status: HubStatus = HubStatus.Checking
)
