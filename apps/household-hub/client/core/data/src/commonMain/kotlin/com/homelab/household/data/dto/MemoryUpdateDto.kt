package com.homelab.household.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MemoryUpdateDto(
    val content: String? = null,
    val confidence: Float? = null,
    @SerialName("is_active") val is_active: Boolean? = null,
)
