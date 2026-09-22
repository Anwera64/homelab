package com.homelab.household.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class SpaceUpdateDto(
    val settings: Map<String, String> = emptyMap(),
)
