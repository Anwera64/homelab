package com.homelab.household.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `GET /api/v1/memories/page`: the member's personal memories and every household memory. */
@Serializable
data class MemoryPageDto(
    val yours: List<MemoryPageItemDto> = emptyList(),
    val household: List<MemoryPageItemDto> = emptyList(),
)

@Serializable
data class MemoryPageItemDto(
    val id: String,
    val content: String,
    val scope: String = "personal",
    val origin: String = "reflection",
    @SerialName("created_at") val created_at: String,
    @SerialName("expires_at") val expires_at: String? = null,
    @SerialName("speaker_id") val speaker_id: String,
    @SerialName("speaker_name") val speaker_name: String = "",
    @SerialName("is_mine") val is_mine: Boolean = false,
    @SerialName("agent_name") val agent_name: String,
    @SerialName("agent_avatar") val agent_avatar: String,
    @SerialName("can_remove") val can_remove: Boolean = false,
)
