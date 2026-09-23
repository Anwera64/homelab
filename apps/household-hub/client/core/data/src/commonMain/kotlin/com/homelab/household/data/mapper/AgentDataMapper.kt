package com.homelab.household.data.mapper

import com.homelab.household.data.dto.AgentCreateDto
import com.homelab.household.data.dto.AgentReadDto
import com.homelab.household.data.dto.AgentUpdateDto
import com.homelab.household.domain.model.AgentPersonality

object AgentDataMapper {
    fun toDomain(dto: AgentReadDto): AgentPersonality =
        AgentPersonality(
            id = dto.id,
            slug = dto.slug,
            name = dto.name,
            description = dto.description,
            avatar = dto.avatar,
            systemPrompt = dto.system_prompt,
            temperature = dto.temperature,
            topP = dto.top_p,
            toolPermissions = dto.tool_permissions,
            isBuiltin = dto.is_builtin,
            isActive = dto.is_active,
            ownerId = dto.owner_id,
            deletedAt = dto.deleted_at,
            createdAt = dto.created_at,
            updatedAt = dto.updated_at,
        )

    fun toCreateDto(agent: AgentPersonality): AgentCreateDto =
        AgentCreateDto(
            slug = agent.slug,
            name = agent.name,
            description = agent.description,
            avatar = agent.avatar,
            system_prompt = agent.systemPrompt,
            temperature = agent.temperature,
            top_p = agent.topP,
            tool_permissions = agent.toolPermissions,
        )

    fun toUpdateDto(agent: AgentPersonality): AgentUpdateDto =
        AgentUpdateDto(
            name = agent.name,
            description = agent.description,
            avatar = agent.avatar,
            system_prompt = agent.systemPrompt,
            temperature = agent.temperature,
            top_p = agent.topP,
            tool_permissions = agent.toolPermissions,
            is_active = agent.isActive,
        )
}
