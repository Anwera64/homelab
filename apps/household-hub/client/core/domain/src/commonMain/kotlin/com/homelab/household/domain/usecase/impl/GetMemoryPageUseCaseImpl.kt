package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.model.MemoryPage
import com.homelab.household.domain.repository.MemoryRepository
import com.homelab.household.domain.usecase.GetMemoryPageUseCase

class GetMemoryPageUseCaseImpl(
    private val memoryRepository: MemoryRepository,
) : GetMemoryPageUseCase {
    override suspend operator fun invoke(): MemoryPage = memoryRepository.getMemoryPage()
}
