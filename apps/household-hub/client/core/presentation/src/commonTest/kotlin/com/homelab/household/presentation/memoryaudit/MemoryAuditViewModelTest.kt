package com.homelab.household.presentation.memoryaudit

import com.homelab.household.domain.model.AgentMemory
import com.homelab.household.domain.model.HouseholdMilestone
import com.homelab.household.domain.model.MemoryScope
import com.homelab.household.domain.usecase.AuditMemoriesUseCase
import com.homelab.household.domain.usecase.RevokeMemoryUseCase
import com.homelab.household.domain.usecase.RevokeMilestoneUseCase
import com.homelab.household.domain.usecase.UpdateMemoryUseCase
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class MemoryAuditViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val auditMemoriesUseCase = mock<AuditMemoriesUseCase>()
    private val deleteMemoryUseCase = mock<RevokeMemoryUseCase>()
    private val updateMemoryUseCase = mock<UpdateMemoryUseCase>()
    private val revokeMilestoneUseCase = mock<RevokeMilestoneUseCase>()

    private lateinit var viewModel: MemoryAuditViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = MemoryAuditViewModel(
            auditMemoriesUseCase = auditMemoriesUseCase,
            deleteMemoryUseCase = deleteMemoryUseCase,
            updateMemoryUseCase = updateMemoryUseCase,
            revokeMilestoneUseCase = revokeMilestoneUseCase
        )
    }

    @AfterTest
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
        everySuspend { auditMemoriesUseCase(MemoryScope.PERSONAL) } returns memories

        viewModel.loadAudit(MemoryScope.PERSONAL)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.memories.size)
        assertEquals("Likes coffee", state.memories[0].content)
        assertNull(state.errorMessage)
    }

    @Test
    fun delete_memory_calls_usecase_and_reloads() = runTest(testDispatcher) {
        everySuspend { auditMemoriesUseCase(any()) } returns emptyList()
        everySuspend { deleteMemoryUseCase("mem-1") } returns Unit

        viewModel.deleteMemory("mem-1")
        advanceUntilIdle()

        verifySuspend { deleteMemoryUseCase("mem-1") }
    }

    @Test
    fun revoke_milestone_calls_usecase() = runTest(testDispatcher) {
        everySuspend { revokeMilestoneUseCase("mile-1") } returns Unit

        viewModel.revokeMilestone("mile-1")
        advanceUntilIdle()

        verifySuspend { revokeMilestoneUseCase("mile-1") }
    }
}
