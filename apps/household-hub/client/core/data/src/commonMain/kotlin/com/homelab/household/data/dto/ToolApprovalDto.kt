package com.homelab.household.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One write agents can do, and whether they do it for the member without asking. */
@Serializable
data class ToolApprovalDto(
    val tool: String,
    val action: String,
    val auto: Boolean = false,
    @SerialName("always_asks") val alwaysAsks: Boolean = false,
)

@Serializable
data class ToolApprovalUpdateDto(
    val tool: String,
    val action: String,
    val auto: Boolean,
)
