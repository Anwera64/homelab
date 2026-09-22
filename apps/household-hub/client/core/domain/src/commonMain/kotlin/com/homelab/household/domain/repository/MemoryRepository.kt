package com.homelab.household.domain.repository

import com.homelab.household.domain.model.AgentMemory
import com.homelab.household.domain.model.MemoryScope

interface MemoryRepository {
    suspend fun auditMemories(scope: MemoryScope? = null): List<AgentMemory>

    suspend fun deleteMemory(memoryId: String)

    suspend fun updateMemory(
        memoryId: String,
        content: String? = null,
        confidence: Float? = null,
        isActive: Boolean? = null,
    ): AgentMemory
}
