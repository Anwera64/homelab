package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.ChangesToCheck
import com.homelab.household.domain.model.LookedOver
import com.homelab.household.domain.model.MemoryItem
import com.homelab.household.domain.model.MemoryOrigin
import com.homelab.household.domain.model.MemoryPage
import com.homelab.household.domain.model.MemoryScope
import com.homelab.household.domain.model.ProposalAnswer
import com.homelab.household.domain.repository.MemoryRepository
import com.homelab.household.domain.usecase.impl.AnswerChangeUseCaseImpl
import com.homelab.household.domain.usecase.impl.GetChangesToCheckUseCaseImpl
import com.homelab.household.domain.usecase.impl.GetLookedOverUseCaseImpl
import com.homelab.household.domain.usecase.impl.GetMemoryPageUseCaseImpl
import com.homelab.household.domain.usecase.impl.RemoveMemoryUseCaseImpl
import com.homelab.household.domain.usecase.impl.RestoreMemoryUseCaseImpl
import com.homelab.household.domain.usecase.impl.UndoChangeAnswerUseCaseImpl
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** The memory page's three asks: the page, ✕, and Undo. Each is one call to the repository. */
@OptIn(ExperimentalTime::class)
class MemoryUseCasesTest {
    private val repository = mock<MemoryRepository>()

    private val item =
        MemoryItem(
            id = "mem-1",
            content = "Prefers dinner after 20:00 on weekdays.",
            scope = MemoryScope.PERSONAL,
            origin = MemoryOrigin.Reflection,
            createdAt = Instant.parse("2026-10-15T09:00:00Z"),
            expiresAt = null,
            speakerId = "emma",
            speakerName = "Emma",
            isMine = true,
            agentName = "Coordinator",
            agentAvatar = "🏡",
            canRemove = true,
        )

    @Test
    fun `GIVEN memories on the hub WHEN the page is asked for THEN the repository's page comes back`() =
        runTest {
            val page = MemoryPage(yours = listOf(item), household = emptyList())
            everySuspend { repository.getMemoryPage() } returns page

            assertEquals(page, GetMemoryPageUseCaseImpl(repository)())
        }

    @Test
    fun `GIVEN a memory WHEN it is removed THEN the repository is asked to remove it`() =
        runTest {
            everySuspend { repository.removeMemory("mem-1") } returns Unit

            RemoveMemoryUseCaseImpl(repository)("mem-1")

            verifySuspend(VerifyMode.exactly(1)) { repository.removeMemory("mem-1") }
        }

    @Test
    fun `GIVEN a removed memory WHEN Undo is tapped THEN the repository is asked to restore it`() =
        runTest {
            everySuspend { repository.restoreMemory("mem-1") } returns Unit

            RestoreMemoryUseCaseImpl(repository)("mem-1")

            verifySuspend(VerifyMode.exactly(1)) { repository.restoreMemory("mem-1") }
        }

    @Test
    fun `GIVEN changes on the hub WHEN they are asked for THEN the repository's lists come back`() =
        runTest {
            val changes = ChangesToCheck(toCheck = emptyList(), waiting = emptyList())
            everySuspend { repository.getChangesToCheck() } returns changes

            assertEquals(changes, GetChangesToCheckUseCaseImpl(repository)())
        }

    @Test
    fun `GIVEN a change WHEN it is answered and undone THEN the repository is asked for each`() =
        runTest {
            everySuspend { repository.answerChange("p-1", ProposalAnswer.UseNew) } returns Unit
            everySuspend { repository.undoChangeAnswer("p-1") } returns Unit

            AnswerChangeUseCaseImpl(repository)("p-1", ProposalAnswer.UseNew)
            UndoChangeAnswerUseCaseImpl(repository)("p-1")

            verifySuspend(VerifyMode.exactly(1)) { repository.answerChange("p-1", ProposalAnswer.UseNew) }
            verifySuspend(VerifyMode.exactly(1)) { repository.undoChangeAnswer("p-1") }
        }

    @Test
    fun `GIVEN a looked-over chat WHEN its sheet is asked for THEN the repository's sheet comes back`() =
        runTest {
            val sheet =
                LookedOver(
                    at = Instant.parse("2026-10-16T10:52:00Z"),
                    toCheck = emptyList(),
                    waiting = emptyList(),
                    remembered = listOf(item),
                )
            everySuspend { repository.getLookedOver("chat-1") } returns sheet

            assertEquals(sheet, GetLookedOverUseCaseImpl(repository)("chat-1"))
        }
}
