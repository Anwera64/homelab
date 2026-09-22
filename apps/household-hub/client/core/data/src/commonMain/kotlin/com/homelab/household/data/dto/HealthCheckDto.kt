package com.homelab.household.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class HealthCheckDto(
    val status: String,
    val version: String? = null,
    val database: String? = null,
)
