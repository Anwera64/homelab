package com.homelab.household.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ToolApprovalResponseDto(
    val status: String,
    @SerialName("tool_call_id") val tool_call_id: String? = null,
    val result: JsonObject? = null,
)
