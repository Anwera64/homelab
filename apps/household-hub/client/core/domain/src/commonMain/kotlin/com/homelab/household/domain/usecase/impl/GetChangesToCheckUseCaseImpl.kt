package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.model.ChangesToCheck
import com.homelab.household.domain.repository.MemoryRepository
import com.homelab.household.domain.usecase.GetChangesToCheckUseCase

class GetChangesToCheckUseCaseImpl(
    private val memoryRepository: MemoryRepository,
) : GetChangesToCheckUseCase {
    override suspend operator fun invoke(): ChangesToCheck = memoryRepository.getChangesToCheck()
}
