package com.homelab.household.data.repository

import com.homelab.household.data.datasource.remote.`interface`.MemoryRemoteDataSource
import com.homelab.household.data.dto.MemoryUpdateDto
import com.homelab.household.data.mapper.MemoryDataMapper
import com.homelab.household.domain.model.AgentMemory
import com.homelab.household.domain.model.MemoryScope
import com.homelab.household.domain.repository.MemoryRepository

/**
 * Orchestration and mapping for what the agents remember. [remote] speaks DTOs and the wire's
 * lowercase scope name; every DTO becomes an `AgentMemory` here.
 */
class MemoryRepositoryImpl(
    private val remote: MemoryRemoteDataSource,
) : MemoryRepository {
    override suspend fun auditMemories(scope: MemoryScope?): List<AgentMemory> =
        remote.auditMemories(scope?.name?.lowercase()).map(MemoryDataMapper::toDomain)

    override suspend fun deleteMemory(memoryId: String) = remote.deleteMemory(memoryId)

    override suspend fun updateMemory(
        memoryId: String,
        content: String?,
        confidence: Float?,
        isActive: Boolean?,
    ): AgentMemory =
        MemoryDataMapper.toDomain(
            remote.updateMemory(
                memoryId,
                MemoryUpdateDto(content = content, confidence = confidence, is_active = isActive),
            ),
        )
}
