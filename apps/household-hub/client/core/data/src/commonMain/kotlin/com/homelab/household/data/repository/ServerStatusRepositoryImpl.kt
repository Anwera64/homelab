package com.homelab.household.data.repository

import com.homelab.household.data.datasource.remote.`interface`.ServerStatusRemoteDataSource
import com.homelab.household.domain.model.ServerStatus
import com.homelab.household.domain.repository.ServerStatusRepository
import kotlinx.coroutines.flow.Flow

/**
 * Nothing to map and nothing to decide: whether the hub is reachable is already the answer the
 * screens want. This was the only repository already shaped this way before the rework, and the
 * rest of `:core:data` was moved to match it.
 */
class ServerStatusRepositoryImpl(
    private val remote: ServerStatusRemoteDataSource,
) : ServerStatusRepository {
    override fun observeServerStatus(): Flow<ServerStatus> = remote.observeStatus()

    override suspend fun checkHealth(): ServerStatus = remote.checkHealth()
}
