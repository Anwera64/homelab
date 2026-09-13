package com.homelab.household.domain.usecase

import com.homelab.household.domain.assertThrowsSuspend
import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.model.AgentMemory
import com.homelab.household.domain.model.HouseholdMilestone
import com.homelab.household.domain.model.MemoryScope
import com.homelab.household.domain.repository.GossipRepository
import com.homelab.household.domain.repository.MemoryRepository
import com.homelab.household.domain.usecase.impl.AuditMemoriesUseCaseImpl
import com.homelab.household.domain.usecase.impl.ListHouseholdMilestonesUseCaseImpl
import com.homelab.household.domain.usecase.impl.RevokeMemoryUseCaseImpl
import com.homelab.household.domain.usecase.impl.RevokeMilestoneUseCaseImpl
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GossipAndMemoryUseCasesTest {

    private val gossipRepo = mockk<GossipRepository>()
    private val memoryRepo = mockk<MemoryRepository>()

    private val listMilestonesUseCase = ListHouseholdMilestonesUseCaseImpl(gossipRepo)
    private val revokeMilestoneUseCase = RevokeMilestoneUseCaseImpl(gossipRepo)
    private val auditMemoriesUseCase = AuditMemoriesUseCaseImpl(memoryRepo)
    private val revokeMemoryUseCase = RevokeMemoryUseCaseImpl(memoryRepo)

    private val dummyMilestone = HouseholdMilestone(
        id = "milestone-1",
        sourceUserId = "user-1",
        sourceUsername = "alice",
        reportingAgentId = "agent-1",
        reportingAgentName = "Assistant",
        targetScope = "household",
        category = "event",
        summary = "UPC Studio Review Friday",
        isActive = true,
        createdAt = "2026-09-08T10:00:00Z"
    )

    private val dummyMemory = AgentMemory(
        id = "mem-1",
        userId = "user-1",
        agentId = "agent-1",
        content = "Prefers oat milk in coffee",
        category = "preference",
        scope = MemoryScope.PERSONAL,
        confidence = 0.95f,
        isActive = true,
        createdAt = "2026-09-08T09:00:00Z"
    )

    @Test
    fun list_milestones_returns_active_milestones() = runTest {
        coEvery { gossipRepo.listHouseholdMilestones(limit = 20) } returns listOf(dummyMilestone)

        val result = listMilestonesUseCase(limit = 20)

        assertEquals(1, result.size)
        assertEquals("milestone-1", result.first().id)
    }

    @Test
    fun revoke_milestone_delegates_to_repo() = runTest {
        coEvery { gossipRepo.revokeMilestone("milestone-1") } returns Unit

        revokeMilestoneUseCase("milestone-1")

        coVerify(exactly = 1) { gossipRepo.revokeMilestone("milestone-1") }
    }

    @Test
    fun revoke_milestone_with_blank_id_throws_validation_error() = runTest {
        assertThrowsSuspend<ValidationException> {
            revokeMilestoneUseCase("")
        }
    }

    @Test
    fun audit_memories_returns_memories_list() = runTest {
        coEvery { memoryRepo.auditMemories(scope = MemoryScope.PERSONAL) } returns listOf(dummyMemory)

        val result = auditMemoriesUseCase(scope = MemoryScope.PERSONAL)

        assertEquals(1, result.size)
        assertEquals("mem-1", result.first().id)
        assertEquals(MemoryScope.PERSONAL, result.first().scope)
    }

    @Test
    fun revoke_memory_delegates_to_repo() = runTest {
        coEvery { memoryRepo.deleteMemory("mem-1") } returns Unit

        revokeMemoryUseCase("mem-1")

        coVerify(exactly = 1) { memoryRepo.deleteMemory("mem-1") }
    }
}
