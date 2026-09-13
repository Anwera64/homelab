package com.homelab.household.presentation.launch

/**
 * Everything the launch screen draws: the hub it is calling, what that call found, and — when
 * nothing answered — how long until it asks again by itself.
 */
data class LaunchUiState(
    val hubAddress: String = "",
    val status: HubStatus = HubStatus.Checking,
    val retryInSeconds: Int? = null
)
