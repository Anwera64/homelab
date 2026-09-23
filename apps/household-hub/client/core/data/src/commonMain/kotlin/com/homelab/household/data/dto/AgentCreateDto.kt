package com.homelab.household.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AgentCreateDto(
    val slug: String,
    val name: String,
    val description: String = "",
    val avatar: String = "🤖",
    @SerialName("system_prompt") val system_prompt: String,
    val temperature: Float = 0.7f,
    @SerialName("top_p") val top_p: Float = 0.9f,
    @SerialName("tool_permissions") val tool_permissions: List<String> = emptyList(),
)
