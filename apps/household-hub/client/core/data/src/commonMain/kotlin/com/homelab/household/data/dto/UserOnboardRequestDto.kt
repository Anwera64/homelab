package com.homelab.household.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserOnboardRequestDto(
    val username: String,
    val email: String,
    val password: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("avatar_color") val avatarColor: String? = null
)
