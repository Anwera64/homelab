package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.HouseholdMilestone
import com.homelab.household.domain.repository.GossipRepository

class ListHouseholdMilestonesUseCase(private val gossipRepository: GossipRepository) {
    suspend operator fun invoke(limit: Int = 20): List<HouseholdMilestone> =
        gossipRepository.listHouseholdMilestones(limit)
}
