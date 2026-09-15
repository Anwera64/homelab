package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.AgentMemory

interface UpdateMemoryUseCase {
    suspend operator fun invoke(
        memoryId: String,
        content: String? = null,
        confidence: Float? = null,
        isActive: Boolean? = null
    ): AgentMemory
}
