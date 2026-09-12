package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.HouseholdMilestone
import com.homelab.household.domain.repository.GossipRepository

class ListUserAuditMilestonesUseCase(private val gossipRepository: GossipRepository) {
    suspend operator fun invoke(limit: Int = 50): List<HouseholdMilestone> =
        gossipRepository.listUserAuditMilestones(limit)
}
