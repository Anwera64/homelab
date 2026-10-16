package com.homelab.household.data.repository

import com.homelab.household.data.datasource.remote.`interface`.MemoryRemoteDataSource
import com.homelab.household.data.dto.ChangeCardDto
import com.homelab.household.data.dto.ChangeSourceDto
import com.homelab.household.data.dto.ChangesToCheckDto
import com.homelab.household.data.dto.LookedOverDto
import com.homelab.household.data.dto.MemoryPageDto
import com.homelab.household.data.dto.MemoryPageItemDto
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.MemoryOrigin
import com.homelab.household.domain.model.MemoryScope
import com.homelab.household.domain.model.ProposalAnswer
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

    private fun card(
        id: String,
        title: String?,
        owner: String,
        canAnswer: Boolean = true,
    ) = ChangeCardDto(
        id = id,
        content = "Sam started eating fish again.",
        stored = dto("sam", scope = "household"),
        can_answer = canAnswer,
        answerer_name = "Emma",
        source = ChangeSourceDto(title = title, owner_name = owner, read_at = "2026-10-16T10:42:00Z"),
    )

    @Test
    fun `GIVEN the hub's changes WHEN they are asked for THEN each card comes back with its stored memory and source`() =
        runTest {
            val remote = mock<MemoryRemoteDataSource>()
            everySuspend { remote.getChangesToCheck() } returns
                ChangesToCheckDto(
                    to_check = listOf(card("p1", "Dinner ideas", "Emma")),
                    waiting = listOf(card("p2", null, "Liam", canAnswer = false)),
                )

            val changes = MemoryRepositoryImpl(remote).getChangesToCheck()

            val mine = changes.toCheck.single()
            assertEquals("p1", mine.id)
            assertEquals("sam", mine.stored.id)
            assertEquals(MemoryScope.HOUSEHOLD, mine.stored.scope)
            assertEquals("Dinner ideas", mine.sourceTitle)
            assertEquals(Instant.parse("2026-10-16T10:42:00Z"), mine.readAt)
            val waiting = changes.waiting.single()
            assertEquals(null, waiting.sourceTitle)
            assertEquals("Liam", waiting.sourceOwnerName)
            assertEquals(false, waiting.canAnswer)
        }

    @Test
    fun `GIVEN a change WHEN it is answered and undone THEN the data source is asked for each`() =
        runTest {
            val remote = mock<MemoryRemoteDataSource>()
            everySuspend { remote.answerChange("p1", ProposalAnswer.KeepBoth) } returns Unit
            everySuspend { remote.undoChangeAnswer("p1") } returns Unit
            val repository = MemoryRepositoryImpl(remote)

            repository.answerChange("p1", ProposalAnswer.KeepBoth)
            repository.undoChangeAnswer("p1")

            verifySuspend(VerifyMode.exactly(1)) { remote.answerChange("p1", ProposalAnswer.KeepBoth) }
            verifySuspend(VerifyMode.exactly(1)) { remote.undoChangeAnswer("p1") }
        }

    @Test
    fun `GIVEN a looked-over chat WHEN its sheet is asked for THEN the time, cards and remembered memories map`() =
        runTest {
            val remote = mock<MemoryRemoteDataSource>()
            everySuspend { remote.getLookedOver("chat-1") } returns
                LookedOverDto(
                    at = "2026-10-16T10:52:00",
                    to_check = listOf(card("p1", "Dinner ideas", "Emma")),
                    waiting = emptyList(),
                    remembered = listOf(dto("dairy")),
                )

            val sheet = MemoryRepositoryImpl(remote).getLookedOver("chat-1")

            assertEquals(Instant.parse("2026-10-16T10:52:00Z"), sheet.at)
            assertEquals(listOf("p1"), sheet.toCheck.map { it.id })
            assertEquals(listOf("dairy"), sheet.remembered.map { it.id })
        }

    @Test
    fun `GIVEN a page with changes waiting WHEN it is asked for THEN it carries the count`() =
        runTest {
            val remote = mock<MemoryRemoteDataSource>()
            everySuspend { remote.getMemoryPage() } returns MemoryPageDto(to_check = 2)

            assertEquals(2, MemoryRepositoryImpl(remote).getMemoryPage().toCheck)
        }
}
