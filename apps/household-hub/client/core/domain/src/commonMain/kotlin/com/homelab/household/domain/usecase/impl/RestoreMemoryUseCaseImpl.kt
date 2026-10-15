package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.repository.MemoryRepository
import com.homelab.household.domain.usecase.RestoreMemoryUseCase

class RestoreMemoryUseCaseImpl(
    private val memoryRepository: MemoryRepository,
) : RestoreMemoryUseCase {
    override suspend operator fun invoke(memoryId: String) = memoryRepository.restoreMemory(memoryId)
}
