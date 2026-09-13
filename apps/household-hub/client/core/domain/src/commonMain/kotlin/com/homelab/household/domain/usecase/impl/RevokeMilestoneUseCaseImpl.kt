package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.repository.GossipRepository
import com.homelab.household.domain.usecase.RevokeMilestoneUseCase

class RevokeMilestoneUseCaseImpl(private val gossipRepository: GossipRepository) : RevokeMilestoneUseCase {
    override suspend operator fun invoke(milestoneId: String) {
        if (milestoneId.isBlank()) throw ValidationException("Milestone ID cannot be blank")
        gossipRepository.revokeMilestone(milestoneId)
    }
}
