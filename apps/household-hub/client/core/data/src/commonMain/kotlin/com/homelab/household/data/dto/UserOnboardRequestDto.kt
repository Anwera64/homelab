package com.homelab.household.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserOnboardRequestDto(
    @SerialName("full_name") val fullName: String,
    val pin: String,
    @SerialName("avatar_color") val avatarColor: String,
)
