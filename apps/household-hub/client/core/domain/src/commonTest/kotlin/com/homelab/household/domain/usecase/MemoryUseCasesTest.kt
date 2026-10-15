package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.AgentMemory
import com.homelab.household.domain.model.MemoryScope
import com.homelab.household.domain.repository.MemoryRepository
import com.homelab.household.domain.usecase.impl.AuditMemoriesUseCaseImpl
import com.homelab.household.domain.usecase.impl.RevokeMemoryUseCaseImpl
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MemoryUseCasesTest {
    private val memoryRepo = mock<MemoryRepository>()

    private val auditMemoriesUseCase = AuditMemoriesUseCaseImpl(memoryRepo)
    private val revokeMemoryUseCase = RevokeMemoryUseCaseImpl(memoryRepo)

    private val dummyMemory =
        AgentMemory(
            id = "mem-1",
            userId = "user-1",
            agentId = "agent-1",
            content = "Prefers oat milk in coffee",
            category = "preference",
            scope = MemoryScope.PERSONAL,
            confidence = 0.95f,
            isActive = true,
            createdAt = "2026-09-08T09:00:00Z",
        )

    @Test
    fun audit_memories_returns_memories_list() =
        runTest {
            everySuspend { memoryRepo.auditMemories(scope = MemoryScope.PERSONAL) } returns listOf(dummyMemory)

            val result = auditMemoriesUseCase(scope = MemoryScope.PERSONAL)

            assertEquals(1, result.size)
            assertEquals("mem-1", result.first().id)
            assertEquals(MemoryScope.PERSONAL, result.first().scope)
        }

    @Test
    fun revoke_memory_delegates_to_repo() =
        runTest {
            everySuspend { memoryRepo.deleteMemory("mem-1") } returns Unit

            revokeMemoryUseCase("mem-1")

            verifySuspend(VerifyMode.exactly(1)) { memoryRepo.deleteMemory("mem-1") }
        }
}
