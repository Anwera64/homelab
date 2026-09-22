package com.homelab.household.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ToolApprovalRequestDto(
    @SerialName("tool_call_id") val tool_call_id: String,
    val approved: Boolean,
    @SerialName("modified_arguments") val modified_arguments: JsonObject? = null,
)
