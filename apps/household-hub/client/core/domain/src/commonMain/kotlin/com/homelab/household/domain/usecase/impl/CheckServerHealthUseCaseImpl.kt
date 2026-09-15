package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.model.ServerStatus
import com.homelab.household.domain.repository.ServerStatusRepository
import com.homelab.household.domain.usecase.CheckServerHealthUseCase

class CheckServerHealthUseCaseImpl(
    private val serverStatusRepository: ServerStatusRepository
) : CheckServerHealthUseCase {
    override suspend operator fun invoke(): ServerStatus = serverStatusRepository.checkHealth()
}
