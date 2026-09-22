package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.model.AgentMemory
import com.homelab.household.domain.repository.MemoryRepository
import com.homelab.household.domain.usecase.UpdateMemoryUseCase

class UpdateMemoryUseCaseImpl(
    private val memoryRepository: MemoryRepository,
) : UpdateMemoryUseCase {
    override suspend operator fun invoke(
        memoryId: String,
        content: String?,
        confidence: Float?,
        isActive: Boolean?,
    ): AgentMemory {
        if (memoryId.isBlank()) throw ValidationException("Memory ID cannot be blank")
        return memoryRepository.updateMemory(memoryId, content, confidence, isActive)
    }
}
