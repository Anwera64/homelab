package com.homelab.household.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthStatusDto(
    @SerialName("is_initialized") val is_initialized: Boolean,
    @SerialName("member_count") val member_count: Int,
)
