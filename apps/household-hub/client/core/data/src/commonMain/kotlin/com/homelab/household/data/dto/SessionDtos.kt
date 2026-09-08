package com.homelab.household.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class SessionReadDto(
    val id: String,
    @SerialName("user_id") val user_id: String,
    @SerialName("agent_id") val agent_id: String? = null,
    val title: String = "New Conversation",
    @SerialName("is_secret") val is_secret: Boolean = false,
    @SerialName("is_archived") val is_archived: Boolean = false,
    @SerialName("created_at") val created_at: String? = null,
    @SerialName("updated_at") val updated_at: String? = null
)

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
    val messages: List<ChatMessageReadDto> = emptyList()
)

@Serializable
data class SessionSecretToggleDto(
    @SerialName("is_secret") val is_secret: Boolean
)

@Serializable
data class SessionCreateDto(
    @SerialName("agent_id") val agent_id: String? = null,
    val title: String = "New Conversation",
    @SerialName("is_secret") val is_secret: Boolean = false
)

@Serializable
data class ChatMessageReadDto(
    val id: String,
    @SerialName("session_id") val session_id: String,
    val role: String,
    val content: String,
    @SerialName("created_at") val created_at: String? = null
)

@Serializable
data class ChatTurnRequestDto(
    val content: String,
    @SerialName("auto_approve_writes") val auto_approve_writes: Boolean = false
)

@Serializable
data class ToolApprovalRequestDto(
    @SerialName("tool_call_id") val tool_call_id: String,
    val approved: Boolean,
    @SerialName("modified_arguments") val modified_arguments: JsonObject? = null
)

@Serializable
data class ToolApprovalResponseDto(
    val status: String,
    @SerialName("tool_call_id") val tool_call_id: String? = null,
    val result: JsonObject? = null
)
