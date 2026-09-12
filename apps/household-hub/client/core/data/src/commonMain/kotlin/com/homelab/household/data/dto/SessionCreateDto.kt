package com.homelab.household.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SessionCreateDto(
    @SerialName("agent_id") val agent_id: String? = null,
    val title: String = "New Conversation",
    @SerialName("is_secret") val is_secret: Boolean = false
)
