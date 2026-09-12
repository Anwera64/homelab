package com.homelab.household.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserLoginRequestDto(
    @SerialName("user_id") val userId: String,
    val pin: String
)
