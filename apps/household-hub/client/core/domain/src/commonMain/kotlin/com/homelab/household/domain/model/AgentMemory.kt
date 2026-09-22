package com.homelab.household.domain.model

data class AgentMemory(
    val id: String,
    val userId: String,
    val agentId: String? = null,
    val content: String,
    val category: String = "fact",
    val scope: MemoryScope = MemoryScope.PERSONAL,
    val confidence: Float = 1.0f,
    val isActive: Boolean = true,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)
