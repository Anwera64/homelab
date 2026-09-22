package com.homelab.household.data.datasource.remote.`interface`

import com.homelab.household.data.dto.MemoryReadDto
import com.homelab.household.data.dto.MemoryUpdateDto

/**
 * Everything the hub is asked about what the agents remember. Each function is one call, returning
 * the DTO the hub sent. `MemoryRepositoryImpl` maps those DTOs and turns a `MemoryScope` into the
 * wire's lowercase name.
 */
interface MemoryRemoteDataSource {
    /** [scope] is already the wire value ("personal" or "household"), or null to audit every scope. */
    suspend fun auditMemories(scope: String?): List<MemoryReadDto>

    suspend fun deleteMemory(memoryId: String)

    suspend fun updateMemory(
        memoryId: String,
        memory: MemoryUpdateDto,
    ): MemoryReadDto
}
