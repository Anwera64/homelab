package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.AgentMemory
import com.homelab.household.domain.model.MemoryScope

interface AuditMemoriesUseCase {
    suspend operator fun invoke(scope: MemoryScope? = null): List<AgentMemory>
}
