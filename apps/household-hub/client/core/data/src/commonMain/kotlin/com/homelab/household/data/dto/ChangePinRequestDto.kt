package com.homelab.household.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChangePinRequestDto(
    @SerialName("current_pin") val currentPin: String,
    @SerialName("new_pin") val newPin: String
)
