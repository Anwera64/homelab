package com.homelab.household.data.repository

import com.homelab.household.data.datasource.remote.`interface`.MemoryRemoteDataSource
import com.homelab.household.data.dto.MemoryPageDto
import com.homelab.household.data.dto.MemoryPageItemDto
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.MemoryOrigin
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
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** The memories repository maps the page's DTOs and passes ✕ and Undo straight through. */
@OptIn(ExperimentalTime::class)
class MemoryRepositoryTest {
    private fun dto(
        id: String,
        scope: String = "personal",
    ) = MemoryPageItemDto(
        id = id,
        content = "Dinner together on Saturday 18 October, 20:00.",
        scope = scope,
        origin = "tool",
        created_at = "2026-10-15T09:00:00Z",
        expires_at = null,
        speaker_id = "emma",
        speaker_name = "Emma",
        is_mine = true,
        agent_name = "Coordinator",
        agent_avatar = "🏡",
        can_remove = true,
    )

    @Test
    fun `GIVEN the hub's page WHEN it is asked for THEN each tab comes back as domain memories`() =
        runTest {
            val remote = mock<MemoryRemoteDataSource>()
            everySuspend { remote.getMemoryPage() } returns
                MemoryPageDto(yours = listOf(dto("p1")), household = listOf(dto("h1", scope = "household")))

            val page = MemoryRepositoryImpl(remote).getMemoryPage()

            assertEquals(listOf("p1"), page.yours.map { it.id })
            assertEquals(MemoryScope.HOUSEHOLD, page.household.single().scope)
            assertEquals(MemoryOrigin.Tool, page.yours.single().origin)
            assertEquals(Instant.parse("2026-10-15T09:00:00Z"), page.yours.single().createdAt)
        }

    @Test
    fun `GIVEN the hub cannot be reached WHEN the page is asked for THEN the failure reaches the caller`() =
        runTest {
            val remote = mock<MemoryRemoteDataSource>()
            everySuspend { remote.getMemoryPage() } throws ServerOfflineException()

            assertFailsWith<ServerOfflineException> { MemoryRepositoryImpl(remote).getMemoryPage() }
        }

    @Test
    fun `GIVEN a memory WHEN it is removed and restored THEN the data source is asked for each`() =
        runTest {
            val remote = mock<MemoryRemoteDataSource>()
            everySuspend { remote.removeMemory("mem-1") } returns Unit
            everySuspend { remote.restoreMemory("mem-1") } returns Unit
            val repository = MemoryRepositoryImpl(remote)

            repository.removeMemory("mem-1")
            repository.restoreMemory("mem-1")

            verifySuspend(VerifyMode.exactly(1)) { remote.removeMemory("mem-1") }
            verifySuspend(VerifyMode.exactly(1)) { remote.restoreMemory("mem-1") }
        }
}
