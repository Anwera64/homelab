package com.homelab.household.domain.usecase

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.model.AgentMemory
import com.homelab.household.domain.repository.MemoryRepository

class UpdateMemoryUseCase(private val memoryRepository: MemoryRepository) {
    suspend operator fun invoke(
        memoryId: String,
        content: String? = null,
        confidence: Float? = null,
        isActive: Boolean? = null
    ): AgentMemory {
        if (memoryId.isBlank()) throw ValidationException("Memory ID cannot be blank")
        return memoryRepository.updateMemory(memoryId, content, confidence, isActive)
    }
}
