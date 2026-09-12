package com.homelab.household.domain.usecase

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.repository.GossipRepository

class RevokeMilestoneUseCase(private val gossipRepository: GossipRepository) {
    suspend operator fun invoke(milestoneId: String) {
        if (milestoneId.isBlank()) throw ValidationException("Milestone ID cannot be blank")
        gossipRepository.revokeMilestone(milestoneId)
    }
}
