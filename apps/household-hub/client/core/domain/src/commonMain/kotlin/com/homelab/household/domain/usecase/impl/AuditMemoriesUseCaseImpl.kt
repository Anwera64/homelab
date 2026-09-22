package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.model.AgentMemory
import com.homelab.household.domain.model.MemoryScope
import com.homelab.household.domain.repository.MemoryRepository
import com.homelab.household.domain.usecase.AuditMemoriesUseCase

class AuditMemoriesUseCaseImpl(
    private val memoryRepository: MemoryRepository,
) : AuditMemoriesUseCase {
    override suspend operator fun invoke(scope: MemoryScope?): List<AgentMemory> = memoryRepository.auditMemories(scope)
}
