package com.homelab.household.data.mapper

import com.homelab.household.data.dto.SessionDetailReadDto
import com.homelab.household.data.dto.SessionReadDto
import com.homelab.household.domain.model.ConversationSession

object SessionDataMapper {
    fun toDomain(dto: SessionReadDto): ConversationSession = ConversationSession(
        id = dto.id,
        userId = dto.user_id,
        agentId = dto.agent_id,
        title = dto.title,
        isSecret = dto.is_secret,
        isArchived = dto.is_archived,
        isSecretLocked = false,
        createdAt = dto.created_at,
        updatedAt = dto.updated_at
    )

    fun toDomain(dto: SessionDetailReadDto): ConversationSession = ConversationSession(
        id = dto.id,
        userId = dto.user_id,
        agentId = dto.agent_id,
        title = dto.title,
        isSecret = dto.is_secret,
        isArchived = dto.is_archived,
        isSecretLocked = false,
        createdAt = dto.created_at,
        updatedAt = dto.updated_at
    )
}
