package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.model.LookedOver
import com.homelab.household.domain.repository.MemoryRepository
import com.homelab.household.domain.usecase.GetLookedOverUseCase

class GetLookedOverUseCaseImpl(
    private val memoryRepository: MemoryRepository,
) : GetLookedOverUseCase {
    override suspend operator fun invoke(sessionId: String): LookedOver = memoryRepository.getLookedOver(sessionId)
}
