package com.homelab.household.data.datasource.remote.`interface`

import com.homelab.household.data.dto.GossipMilestoneDto

/**
 * Everything the hub is asked about the household's milestones — what the agents have noticed and
 * reported. Each function is one call, returning the DTOs the hub sent. `GossipRepositoryImpl` maps
 * them to `HouseholdMilestone`.
 */
interface GossipRemoteDataSource {
    suspend fun listHouseholdMilestones(limit: Int): List<GossipMilestoneDto>

    suspend fun listUserAuditMilestones(limit: Int): List<GossipMilestoneDto>

    suspend fun revokeMilestone(milestoneId: String)
}
