package com.homelab.household.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatMessageReadDto(
    val id: String,
    @SerialName("session_id") val session_id: String,
    val role: String,
    val content: String,
    @SerialName("created_at") val created_at: String? = null,
)
