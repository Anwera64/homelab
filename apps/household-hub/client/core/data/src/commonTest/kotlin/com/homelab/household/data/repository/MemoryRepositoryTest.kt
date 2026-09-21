package com.homelab.household.data.repository

import com.homelab.household.data.datasource.remote.`interface`.MemoryRemoteDataSource
import com.homelab.household.data.dto.MemoryReadDto
import com.homelab.household.data.dto.MemoryUpdateDto
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.AgentMemory
import com.homelab.household.domain.model.MemoryScope
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The memories repository maps DTOs to `AgentMemory`, and turns a `MemoryScope` into the wire's
 * lowercase name before it reaches the data source.
 */
class MemoryRepositoryTest {

    private val memoryDto = MemoryReadDto(
        id = "mem-1",
        user_id = "emma",
        agent_id = "a-1",
        content = "Emma prefers tea over coffee",
        scope = "personal",
        confidence = 0.9f,
        created_at = "2026-09-13T00:00:00Z",
    )

    private val memory = AgentMemory(
        id = "mem-1",
        userId = "emma",
        agentId = "a-1",
        content = "Emma prefers tea over coffee",
        scope = MemoryScope.PERSONAL,
        confidence = 0.9f,
        createdAt = "2026-09-13T00:00:00Z",
    )

    private fun repository(remote: MemoryRemoteDataSource) = MemoryRepositoryImpl(remote = remote)

    // ---- auditMemories ------------------------------------------------------

    @Test
    fun `GIVEN a household scope to audit WHEN memories are asked for THEN it is sent to the data source in lowercase`() = runTest {
        // GIVEN
        val remote = mock<MemoryRemoteDataSource>()
        everySuspend { remote.auditMemories("household") } returns listOf(memoryDto)

        // WHEN
        val memories = repository(remote).auditMemories(MemoryScope.HOUSEHOLD)

        // THEN
        assertEquals(listOf(memory), memories)
        verifySuspend(VerifyMode.exactly(1)) { remote.auditMemories("household") }
    }

    @Test
    fun `GIVEN no scope WHEN every memory is audited THEN no scope is sent to the data source`() = runTest {
        // GIVEN
        val remote = mock<MemoryRemoteDataSource>()
        everySuspend { remote.auditMemories(null) } returns listOf(memoryDto)

        // WHEN
        repository(remote).auditMemories(null)

        // THEN
        verifySuspend(VerifyMode.exactly(1)) { remote.auditMemories(null) }
    }

    @Test
    fun `GIVEN the hub cannot be reached WHEN memories are audited THEN the failure reaches the caller`() = runTest {
        // GIVEN
        val remote = mock<MemoryRemoteDataSource>()
        everySuspend { remote.auditMemories(null) } throws ServerOfflineException()

        // WHEN / THEN
        assertFailsWith<ServerOfflineException> { repository(remote).auditMemories(null) }
    }

    // ---- deleteMemory -------------------------------------------------------

    @Test
    fun `GIVEN a memory nobody wants kept WHEN it is deleted THEN the data source is asked to remove it`() = runTest {
        // GIVEN
        val remote = mock<MemoryRemoteDataSource>()
        everySuspend { remote.deleteMemory("mem-1") } returns Unit

        // WHEN
        repository(remote).deleteMemory("mem-1")

        // THEN
        verifySuspend(VerifyMode.exactly(1)) { remote.deleteMemory("mem-1") }
    }

    // ---- updateMemory -------------------------------------------------------

    @Test
    fun `GIVEN changes to a memory WHEN it is updated THEN they are built into an update request and the result comes back mapped`() = runTest {
        // GIVEN
        val remote = mock<MemoryRemoteDataSource>()
        val expectedUpdate = MemoryUpdateDto(content = "Emma prefers tea", confidence = 0.95f, is_active = true)
        everySuspend { remote.updateMemory("mem-1", expectedUpdate) } returns memoryDto

        // WHEN
        val updated = repository(remote).updateMemory(
            "mem-1",
            content = "Emma prefers tea",
            confidence = 0.95f,
            isActive = true,
        )

        // THEN
        assertEquals(memory, updated)
        verifySuspend(VerifyMode.exactly(1)) { remote.updateMemory("mem-1", expectedUpdate) }
    }
}
