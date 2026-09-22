package com.homelab.household.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class InviteCreateRequestDto(
    @SerialName("invited_name") val invitedName: String,
    @SerialName("is_admin") val isAdmin: Boolean,
)
