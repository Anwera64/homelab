package com.homelab.household.domain.model

data class HouseholdMilestone(
    val id: String,
    val sourceUserId: String,
    val sourceUsername: String,
    val reportingAgentId: String? = null,
    val reportingAgentName: String,
    val targetScope: String = "household",
    val category: String = "milestone",
    val summary: String,
    val details: Map<String, Any?> = emptyMap(),
    val expiresAt: String? = null,
    val sourceSessionId: String? = null,
    val isActive: Boolean = true,
    val createdAt: String? = null,
    val updatedAt: String? = null
)
