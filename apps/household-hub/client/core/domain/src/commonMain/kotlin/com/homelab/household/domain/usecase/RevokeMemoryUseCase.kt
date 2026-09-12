package com.homelab.household.domain.usecase

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.repository.MemoryRepository

class RevokeMemoryUseCase(private val memoryRepository: MemoryRepository) {
    suspend operator fun invoke(memoryId: String) {
        if (memoryId.isBlank()) throw ValidationException("Memory ID cannot be blank")
        memoryRepository.deleteMemory(memoryId)
    }
}
