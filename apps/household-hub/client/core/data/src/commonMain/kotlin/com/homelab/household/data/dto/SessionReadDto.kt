package com.homelab.household.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SessionReadDto(
    val id: String,
    @SerialName("user_id") val user_id: String,
    @SerialName("agent_id") val agent_id: String? = null,
    val title: String = "New Conversation",
    @SerialName("is_secret") val is_secret: Boolean = false,
    @SerialName("is_archived") val is_archived: Boolean = false,
    @SerialName("created_at") val created_at: String? = null,
    @SerialName("updated_at") val updated_at: String? = null,
    @SerialName("last_message_preview") val last_message_preview: String? = null,
    @SerialName("agent_name") val agent_name: String? = null,
    @SerialName("agent_avatar") val agent_avatar: String? = null,
)
