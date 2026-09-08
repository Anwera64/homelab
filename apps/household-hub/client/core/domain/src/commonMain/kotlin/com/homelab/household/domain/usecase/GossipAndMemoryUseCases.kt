package com.homelab.household.domain.usecase

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.model.AgentMemory
import com.homelab.household.domain.model.HouseholdMilestone
import com.homelab.household.domain.model.MemoryScope
import com.homelab.household.domain.repository.GossipRepository
import com.homelab.household.domain.repository.MemoryRepository

class ListHouseholdMilestonesUseCase(private val gossipRepository: GossipRepository) {
    suspend operator fun invoke(limit: Int = 20): List<HouseholdMilestone> =
        gossipRepository.listHouseholdMilestones(limit)
}

class ListUserAuditMilestonesUseCase(private val gossipRepository: GossipRepository) {
    suspend operator fun invoke(limit: Int = 50): List<HouseholdMilestone> =
        gossipRepository.listUserAuditMilestones(limit)
}

class RevokeMilestoneUseCase(private val gossipRepository: GossipRepository) {
    suspend operator fun invoke(milestoneId: String) {
        if (milestoneId.isBlank()) throw ValidationException("Milestone ID cannot be blank")
        gossipRepository.revokeMilestone(milestoneId)
    }
}

class AuditMemoriesUseCase(private val memoryRepository: MemoryRepository) {
    suspend operator fun invoke(scope: MemoryScope? = null): List<AgentMemory> =
        memoryRepository.auditMemories(scope)
}

class RevokeMemoryUseCase(private val memoryRepository: MemoryRepository) {
    suspend operator fun invoke(memoryId: String) {
        if (memoryId.isBlank()) throw ValidationException("Memory ID cannot be blank")
        memoryRepository.deleteMemory(memoryId)
    }
}

class UpdateMemoryUseCase(private val memoryRepository: MemoryRepository) {
    suspend operator fun invoke(
        memoryId: String,
        content: String? = null,
        confidence: Float? = null,
        isActive: Boolean? = null
    ): AgentMemory {
        if (memoryId.isBlank()) throw ValidationException("Memory ID cannot be blank")
        return memoryRepository.updateMemory(memoryId, content, confidence, isActive)
    }
}
