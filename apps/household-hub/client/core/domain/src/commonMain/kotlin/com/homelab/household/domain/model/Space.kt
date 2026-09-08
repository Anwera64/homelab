package com.homelab.household.domain.model

enum class SpaceType {
    PERSONAL,
    HOUSEHOLD
}

data class Space(
    val id: String,
    val name: String,
    val type: SpaceType,
    val ownerId: String? = null,
    val settings: Map<String, Any?> = emptyMap(),
    val createdAt: String? = null
)
