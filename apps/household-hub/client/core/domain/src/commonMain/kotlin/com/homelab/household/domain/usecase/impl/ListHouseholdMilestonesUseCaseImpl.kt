package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.model.HouseholdMilestone
import com.homelab.household.domain.repository.GossipRepository
import com.homelab.household.domain.usecase.ListHouseholdMilestonesUseCase

class ListHouseholdMilestonesUseCaseImpl(
    private val gossipRepository: GossipRepository
) : ListHouseholdMilestonesUseCase {
    override suspend operator fun invoke(limit: Int): List<HouseholdMilestone> =
        gossipRepository.listHouseholdMilestones(limit)
}
