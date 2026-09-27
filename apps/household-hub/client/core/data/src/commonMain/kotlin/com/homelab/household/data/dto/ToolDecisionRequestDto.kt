package com.homelab.household.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** The member's answer to one card. [modified_arguments] are details changed on it, over the proposed ones. */
@Serializable
data class ToolDecisionRequestDto(
    val approved: Boolean,
    @SerialName("modified_arguments") val modified_arguments: JsonObject? = null,
)
