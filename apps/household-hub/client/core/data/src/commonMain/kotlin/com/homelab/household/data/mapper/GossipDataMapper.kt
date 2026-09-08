package com.homelab.household.data.mapper

import com.homelab.household.data.dto.GossipMilestoneDto
import com.homelab.household.domain.model.HouseholdMilestone

object GossipDataMapper {
    fun toDomain(dto: GossipMilestoneDto): HouseholdMilestone = HouseholdMilestone(
        id = dto.id,
        sourceUserId = dto.source_user_id,
        sourceUsername = dto.source_username,
        reportingAgentId = dto.reporting_agent_id,
        reportingAgentName = dto.reporting_agent_name,
        targetScope = dto.target_scope,
        category = dto.category,
        summary = dto.summary,
        details = dto.details,
        expiresAt = dto.expires_at,
        sourceSessionId = dto.source_session_id,
        isActive = dto.is_active,
        createdAt = dto.created_at,
        updatedAt = dto.updated_at
    )
}
