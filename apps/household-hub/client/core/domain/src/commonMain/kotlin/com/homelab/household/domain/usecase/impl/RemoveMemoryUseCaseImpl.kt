package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.repository.MemoryRepository
import com.homelab.household.domain.usecase.RemoveMemoryUseCase

class RemoveMemoryUseCaseImpl(
    private val memoryRepository: MemoryRepository,
) : RemoveMemoryUseCase {
    override suspend operator fun invoke(memoryId: String) = memoryRepository.removeMemory(memoryId)
}
