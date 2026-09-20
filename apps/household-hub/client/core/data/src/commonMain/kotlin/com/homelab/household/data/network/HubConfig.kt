package com.homelab.household.data.network

/** The one hub address every repository talks to, along with environment flags. */
data class HubConfig(
    val baseUrl: String,
    val isDebug: Boolean = false,
)
