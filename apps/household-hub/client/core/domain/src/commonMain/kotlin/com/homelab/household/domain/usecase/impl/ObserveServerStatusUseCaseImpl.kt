package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.model.ServerStatus
import com.homelab.household.domain.repository.ServerStatusRepository
import com.homelab.household.domain.usecase.ObserveServerStatusUseCase
import kotlinx.coroutines.flow.Flow

class ObserveServerStatusUseCaseImpl(
    private val serverStatusRepository: ServerStatusRepository
) : ObserveServerStatusUseCase {
    override operator fun invoke(): Flow<ServerStatus> = serverStatusRepository.observeServerStatus()
}
