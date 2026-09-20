package com.homelab.household.data.repository

import com.homelab.household.data.datasource.remote.GossipRemoteDataSource
import com.homelab.household.data.mapper.GossipDataMapper
import com.homelab.household.domain.model.HouseholdMilestone
import com.homelab.household.domain.repository.GossipRepository

/**
 * Orchestration and mapping for the household's milestones. [remote] speaks DTOs; every DTO becomes
 * a `HouseholdMilestone` here.
 */
class GossipRepositoryImpl(
    private val remote: GossipRemoteDataSource,
) : GossipRepository {

    override suspend fun listHouseholdMilestones(limit: Int): List<HouseholdMilestone> =
        remote.listHouseholdMilestones(limit).map(GossipDataMapper::toDomain)

    override suspend fun listUserAuditMilestones(limit: Int): List<HouseholdMilestone> =
        remote.listUserAuditMilestones(limit).map(GossipDataMapper::toDomain)

    override suspend fun revokeMilestone(milestoneId: String) = remote.revokeMilestone(milestoneId)
}
