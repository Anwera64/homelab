package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.ServerStatus
import com.homelab.household.domain.repository.ServerStatusRepository
import kotlinx.coroutines.flow.Flow

class ObserveServerStatusUseCase(private val serverStatusRepository: ServerStatusRepository) {
    operator fun invoke(): Flow<ServerStatus> = serverStatusRepository.observeServerStatus()
}
