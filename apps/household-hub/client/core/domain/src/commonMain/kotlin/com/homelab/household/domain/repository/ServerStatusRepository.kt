package com.homelab.household.domain.repository

import com.homelab.household.domain.model.ServerStatus
import kotlinx.coroutines.flow.Flow

interface ServerStatusRepository {
    fun observeServerStatus(): Flow<ServerStatus>
    suspend fun checkHealth(): ServerStatus
}
