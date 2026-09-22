package com.homelab.household.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class PinResetReadDto(
    val code: String,
    val expires_in_seconds: Int,
)
