package com.homelab.household.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatTurnRequestDto(
    val content: String,
    @SerialName("auto_approve_writes") val auto_approve_writes: Boolean = false,
)
