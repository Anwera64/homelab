package com.homelab.household.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SessionDetailReadDto(
    val id: String,
    @SerialName("user_id") val user_id: String,
    @SerialName("agent_id") val agent_id: String? = null,
    val title: String = "New Conversation",
    @SerialName("is_secret") val is_secret: Boolean = false,
    @SerialName("is_archived") val is_archived: Boolean = false,
    @SerialName("created_at") val created_at: String? = null,
    @SerialName("updated_at") val updated_at: String? = null,
    val messages: List<ChatMessageReadDto> = emptyList(),
    @SerialName("turn_running") val turn_running: Boolean = false,
)
