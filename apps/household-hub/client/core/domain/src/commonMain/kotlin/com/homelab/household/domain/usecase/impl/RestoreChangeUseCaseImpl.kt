package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.repository.MemoryRepository
import com.homelab.household.domain.usecase.RestoreChangeUseCase

class RestoreChangeUseCaseImpl(
    private val memoryRepository: MemoryRepository,
) : RestoreChangeUseCase {
    override suspend operator fun invoke(changeId: String) = memoryRepository.restoreChange(changeId)
}
