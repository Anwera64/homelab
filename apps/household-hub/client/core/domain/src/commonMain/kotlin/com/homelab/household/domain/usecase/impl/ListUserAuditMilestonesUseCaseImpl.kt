package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.model.HouseholdMilestone
import com.homelab.household.domain.repository.GossipRepository
import com.homelab.household.domain.usecase.ListUserAuditMilestonesUseCase

class ListUserAuditMilestonesUseCaseImpl(
    private val gossipRepository: GossipRepository
) : ListUserAuditMilestonesUseCase {
    override suspend operator fun invoke(limit: Int): List<HouseholdMilestone> =
        gossipRepository.listUserAuditMilestones(limit)
}
