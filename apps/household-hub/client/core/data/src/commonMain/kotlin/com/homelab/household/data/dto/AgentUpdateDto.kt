package com.homelab.household.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AgentUpdateDto(
    val name: String? = null,
    val description: String? = null,
    val avatar: String? = null,
    @SerialName("system_prompt") val system_prompt: String? = null,
    @SerialName("model_alias") val model_alias: String? = null,
    val temperature: Float? = null,
    @SerialName("top_p") val top_p: Float? = null,
    @SerialName("tool_permissions") val tool_permissions: List<String>? = null,
    @SerialName("is_active") val is_active: Boolean? = null,
)
