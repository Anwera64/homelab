package com.homelab.household.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class SpaceReadDto(
    val id: String,
    val name: String,
    val type: String,
    @SerialName("owner_id") val owner_id: String? = null,
    val settings: Map<String, String> = emptyMap(),
    @SerialName("created_at") val created_at: String? = null
)

@Serializable
data class SpaceUpdateDto(
    val settings: Map<String, String> = emptyMap()
)
