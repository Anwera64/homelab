package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.ServerStatus
import com.homelab.household.domain.repository.ServerStatusRepository

class CheckServerHealthUseCase(private val serverStatusRepository: ServerStatusRepository) {
    suspend operator fun invoke(): ServerStatus = serverStatusRepository.checkHealth()
}
