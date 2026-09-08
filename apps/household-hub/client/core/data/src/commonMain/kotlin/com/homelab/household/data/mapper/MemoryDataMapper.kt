package com.homelab.household.data.mapper

import com.homelab.household.data.dto.MemoryReadDto
import com.homelab.household.domain.model.AgentMemory
import com.homelab.household.domain.model.MemoryScope

object MemoryDataMapper {
    fun toDomain(dto: MemoryReadDto): AgentMemory = AgentMemory(
        id = dto.id,
        userId = dto.user_id,
        agentId = dto.agent_id,
        content = dto.content,
        category = dto.category,
        scope = if (dto.scope.equals("household", ignoreCase = true)) MemoryScope.HOUSEHOLD else MemoryScope.PERSONAL,
        confidence = dto.confidence,
        isActive = dto.is_active,
        createdAt = dto.created_at,
        updatedAt = dto.updated_at
    )
}
