package com.homelab.household.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MemoryReadDto(
    val id: String,
    @SerialName("user_id") val user_id: String,
    @SerialName("agent_id") val agent_id: String? = null,
    val content: String,
    val category: String = "fact",
    val scope: String = "personal",
    val confidence: Float = 1.0f,
    @SerialName("is_active") val is_active: Boolean = true,
    @SerialName("created_at") val created_at: String? = null,
    @SerialName("updated_at") val updated_at: String? = null
)

@Serializable
data class MemoryUpdateDto(
    val content: String? = null,
    val confidence: Float? = null,
    @SerialName("is_active") val is_active: Boolean? = null
)
