package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.ServerStatus
import com.homelab.household.domain.repository.ServerStatusRepository
import kotlinx.coroutines.flow.Flow

class ObserveServerStatusUseCase(private val serverStatusRepository: ServerStatusRepository) {
    operator fun invoke(): Flow<ServerStatus> = serverStatusRepository.observeServerStatus()
}

class CheckServerHealthUseCase(private val serverStatusRepository: ServerStatusRepository) {
    suspend operator fun invoke(): ServerStatus = serverStatusRepository.checkHealth()
}
