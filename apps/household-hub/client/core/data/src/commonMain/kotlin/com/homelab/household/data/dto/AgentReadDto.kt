package com.homelab.household.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AgentReadDto(
    val id: String,
    val slug: String,
    val name: String,
    val description: String = "",
    val avatar: String = "🤖",
    @SerialName("system_prompt") val system_prompt: String,
    @SerialName("model_alias") val model_alias: String = "qwen3:14b",
    val temperature: Float = 0.7f,
    @SerialName("top_p") val top_p: Float = 0.9f,
    @SerialName("tool_permissions") val tool_permissions: List<String> = emptyList(),
    @SerialName("is_builtin") val is_builtin: Boolean = false,
    @SerialName("is_active") val is_active: Boolean = true,
    @SerialName("owner_id") val owner_id: String? = null,
    @SerialName("deleted_at") val deleted_at: String? = null,
    @SerialName("created_at") val created_at: String? = null,
    @SerialName("updated_at") val updated_at: String? = null
)
