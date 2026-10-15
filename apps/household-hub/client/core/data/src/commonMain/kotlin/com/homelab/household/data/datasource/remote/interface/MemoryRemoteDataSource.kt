package com.homelab.household.data.datasource.remote.`interface`

import com.homelab.household.data.dto.MemoryPageDto

/**
 * Everything the hub is asked about what the agents remember. Each function is one call, returning
 * the DTO the hub sent; `MemoryRepositoryImpl` maps them.
 */
interface MemoryRemoteDataSource {
    suspend fun getMemoryPage(): MemoryPageDto

    suspend fun removeMemory(memoryId: String)

    suspend fun restoreMemory(memoryId: String)
}
