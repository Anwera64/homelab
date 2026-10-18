package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.repository.MemoryRepository
import com.homelab.household.domain.usecase.WithdrawChangeUseCase

class WithdrawChangeUseCaseImpl(
    private val memoryRepository: MemoryRepository,
) : WithdrawChangeUseCase {
    override suspend operator fun invoke(changeId: String) = memoryRepository.withdrawChange(changeId)
}
