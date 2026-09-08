package com.homelab.household.domain.repository

import com.homelab.household.domain.model.HouseholdMilestone

interface GossipRepository {
    suspend fun listHouseholdMilestones(limit: Int = 20): List<HouseholdMilestone>
    suspend fun listUserAuditMilestones(limit: Int = 50): List<HouseholdMilestone>
    suspend fun revokeMilestone(milestoneId: String)
}
