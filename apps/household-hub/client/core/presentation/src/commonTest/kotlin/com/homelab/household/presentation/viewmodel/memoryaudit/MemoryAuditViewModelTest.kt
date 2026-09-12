package com.homelab.household.presentation.viewmodel.memoryaudit

import com.homelab.household.domain.model.AgentMemory
import com.homelab.household.domain.model.HouseholdMilestone
import com.homelab.household.domain.model.MemoryScope
import com.homelab.household.domain.usecase.AuditMemoriesUseCase
import com.homelab.household.domain.usecase.RevokeMemoryUseCase
import com.homelab.household.domain.usecase.RevokeMilestoneUseCase
import com.homelab.household.domain.usecase.UpdateMemoryUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MemoryAuditViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val auditMemoriesUseCase = mockk<AuditMemoriesUseCase>()
    private val deleteMemoryUseCase = mockk<RevokeMemoryUseCase>()
    private val updateMemoryUseCase = mockk<UpdateMemoryUseCase>()
    private val revokeMilestoneUseCase = mockk<RevokeMilestoneUseCase>()

    private lateinit var viewModel: MemoryAuditViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = MemoryAuditViewModel(
            auditMemoriesUseCase = auditMemoriesUseCase,
            deleteMemoryUseCase = deleteMemoryUseCase,
            updateMemoryUseCase = updateMemoryUseCase,
            revokeMilestoneUseCase = revokeMilestoneUseCase
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun load_audit_loads_memories_for_given_scope() = runTest(testDispatcher) {
        val memories = listOf(
            AgentMemory(
                id = "mem-1",
                userId = "u-1",
                content = "Likes coffee",
                scope = MemoryScope.PERSONAL
            )
        )
        coEvery { auditMemoriesUseCase(MemoryScope.PERSONAL) } returns memories

        viewModel.loadAudit(MemoryScope.PERSONAL)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.memories.size)
        assertEquals("Likes coffee", state.memories[0].content)
        assertNull(state.errorMessage)
    }

    @Test
    fun delete_memory_calls_usecase_and_reloads() = runTest(testDispatcher) {
        coEvery { auditMemoriesUseCase(any()) } returns emptyList()
        coEvery { deleteMemoryUseCase("mem-1") } returns Unit

        viewModel.deleteMemory("mem-1")
        advanceUntilIdle()

        coVerify { deleteMemoryUseCase("mem-1") }
    }

    @Test
    fun revoke_milestone_calls_usecase() = runTest(testDispatcher) {
        coEvery { revokeMilestoneUseCase("mile-1") } returns Unit

        viewModel.revokeMilestone("mile-1")
        advanceUntilIdle()

        coVerify { revokeMilestoneUseCase("mile-1") }
    }
}
