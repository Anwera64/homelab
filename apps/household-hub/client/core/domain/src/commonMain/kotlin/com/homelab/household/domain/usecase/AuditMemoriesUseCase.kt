package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.AgentMemory
import com.homelab.household.domain.model.MemoryScope
import com.homelab.household.domain.repository.MemoryRepository

class AuditMemoriesUseCase(private val memoryRepository: MemoryRepository) {
    suspend operator fun invoke(scope: MemoryScope? = null): List<AgentMemory> =
        memoryRepository.auditMemories(scope)
}
