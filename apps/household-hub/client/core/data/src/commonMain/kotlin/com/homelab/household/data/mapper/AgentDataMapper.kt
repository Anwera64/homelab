package com.homelab.household.data.mapper

import com.homelab.household.data.dto.AgentReadDto
import com.homelab.household.domain.model.AgentPersonality

object AgentDataMapper {
    fun toDomain(dto: AgentReadDto): AgentPersonality = AgentPersonality(
        id = dto.id,
        slug = dto.slug,
        name = dto.name,
        description = dto.description,
        avatar = dto.avatar,
        systemPrompt = dto.system_prompt,
        modelAlias = dto.model_alias,
        temperature = dto.temperature,
        topP = dto.top_p,
        toolPermissions = dto.tool_permissions,
        isBuiltin = dto.is_builtin,
        isActive = dto.is_active,
        ownerId = dto.owner_id,
        deletedAt = dto.deleted_at,
        createdAt = dto.created_at,
        updatedAt = dto.updated_at
    )
}
