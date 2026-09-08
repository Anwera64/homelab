package com.homelab.household.data.repository

import com.homelab.household.data.remote.ServerHealthMonitor
import com.homelab.household.domain.model.ServerStatus
import com.homelab.household.domain.repository.ServerStatusRepository
import kotlinx.coroutines.flow.Flow

class ServerStatusRepositoryImpl(
    private val healthMonitor: ServerHealthMonitor
) : ServerStatusRepository {

    override fun observeServerStatus(): Flow<ServerStatus> {
        return healthMonitor.observeStatus()
    }

    override suspend fun checkHealth(): ServerStatus {
        return healthMonitor.checkHealth()
    }
}
