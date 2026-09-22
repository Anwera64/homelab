package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.repository.MemoryRepository
import com.homelab.household.domain.usecase.RevokeMemoryUseCase

class RevokeMemoryUseCaseImpl(
    private val memoryRepository: MemoryRepository,
) : RevokeMemoryUseCase {
    override suspend operator fun invoke(memoryId: String) {
        if (memoryId.isBlank()) throw ValidationException("Memory ID cannot be blank")
        memoryRepository.deleteMemory(memoryId)
    }
}
