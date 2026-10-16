package com.homelab.household.presentation.memories

import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.LookedOver
import com.homelab.household.domain.model.MemoryItem
import com.homelab.household.domain.model.MemoryOrigin
import com.homelab.household.domain.model.MemoryScope
import com.homelab.household.domain.model.ProposalAnswer
import com.homelab.household.domain.usecase.AnswerChangeUseCase
import com.homelab.household.domain.usecase.GetLookedOverUseCase
import com.homelab.household.domain.usecase.RemoveMemoryUseCase
import com.homelab.household.domain.usecase.RestoreMemoryUseCase
import com.homelab.household.domain.usecase.UndoChangeAnswerUseCase
import com.homelab.household.presentation.memories.ChangesToCheckViewModelTest.Companion.card
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** "Remembered from this chat": changes answered in place, the waiting ones, and ✕ on what was saved. */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class LookedOverViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val getLookedOver = mock<GetLookedOverUseCase>()
    private val answerChange = mock<AnswerChangeUseCase>()
    private val undoAnswer = mock<UndoChangeAnswerUseCase>()
    private val removeMemory = mock<RemoveMemoryUseCase>()
    private val restoreMemory = mock<RestoreMemoryUseCase>()
    private val clock =
        object : Clock {
            override fun now() = Instant.parse("2026-10-16T18:00:00Z")
        }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val dairy =
        MemoryItem(
            id = "dairy",
            content = "Is off dairy this month.",
            scope = MemoryScope.PERSONAL,
            origin = MemoryOrigin.Reflection,
            createdAt = Instant.parse("2026-10-16T10:52:00Z"),
            expiresAt = Instant.parse("2026-10-31T22:00:00Z"),
            speakerId = "emma",
            speakerName = "Emma",
            isMine = true,
            agentName = "Coordinator",
            agentAvatar = "🏡",
            canRemove = true,
        )

    private val sheet =
        LookedOver(
            at = Instant.parse("2026-10-16T10:52:00Z"),
            toCheck = listOf(card("p-sam")),
            waiting = listOf(card("p-boiler", canAnswer = false, answerer = "Liam")),
            remembered = listOf(dairy),
        )

    private fun viewModel() =
        LookedOverViewModel(
            getLookedOver,
            answerChange,
            undoAnswer,
            removeMemory,
            restoreMemory,
            deviceTimeZone = { 0 },
            clock = clock,
        )

    @Test
    fun `GIVEN a looked-over chat WHEN the sheet opens THEN it shows the time, the changes and what was remembered`() =
        runTest(testDispatcher) {
            everySuspend { getLookedOver("chat-1") } returns sheet
            val viewModel = viewModel()

            viewModel.open("chat-1")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(MemoriesStatus.Ready, state.status)
            assertEquals(LocalTime(hour = 10, minute = 52), state.at)
            assertEquals(listOf("p-sam"), state.toCheck.map { it.card.id })
            assertEquals(listOf("Liam"), state.waitingByAnswerer.map { it.first })
            assertEquals(listOf("dairy"), state.remembered.map { it.item.id })
            assertEquals(LocalDay(day = 31, month = 10), state.remembered.single().until)
            assertEquals(1, state.openCount)
            assertEquals(1, state.rememberedCount)
        }

    @Test
    fun `GIVEN the hub can't be reached WHEN the sheet opens THEN it says so and Try again reloads`() =
        runTest(testDispatcher) {
            everySuspend { getLookedOver("chat-1") } throws ServerOfflineException("offline")
            val viewModel = viewModel()
            viewModel.open("chat-1")
            advanceUntilIdle()
            assertEquals(MemoriesStatus.Unreachable, viewModel.uiState.value.status)

            everySuspend { getLookedOver("chat-1") } returns sheet
            viewModel.retry()
            advanceUntilIdle()

            assertEquals(MemoriesStatus.Ready, viewModel.uiState.value.status)
        }

    @Test
    fun `GIVEN a change in the sheet WHEN it is answered and undone THEN it works as on the page`() =
        runTest(testDispatcher) {
            everySuspend { getLookedOver("chat-1") } returns sheet
            everySuspend { answerChange(any(), any()) } returns Unit
            everySuspend { undoAnswer(any()) } returns Unit
            val viewModel = viewModel()
            viewModel.open("chat-1")
            advanceUntilIdle()

            viewModel.onAnswer("p-sam", ProposalAnswer.UseNew)
            advanceUntilIdle()
            assertEquals(ChangeStatus.Done(ProposalAnswer.UseNew), viewModel.uiState.value.toCheck.single().status)
            assertEquals(0, viewModel.uiState.value.openCount)

            viewModel.onUndo("p-sam")
            advanceUntilIdle()
            assertEquals(ChangeStatus.Open(), viewModel.uiState.value.toCheck.single().status)
            verifySuspend(VerifyMode.exactly(1)) { undoAnswer("p-sam") }
        }

    @Test
    fun `GIVEN a remembered memory WHEN its X is tapped THEN it is removed in place with Undo`() =
        runTest(testDispatcher) {
            everySuspend { getLookedOver("chat-1") } returns sheet
            everySuspend { removeMemory("dairy") } returns Unit
            everySuspend { restoreMemory("dairy") } returns Unit
            val viewModel = viewModel()
            viewModel.open("chat-1")
            advanceUntilIdle()

            viewModel.onRemove("dairy")
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.remembered.single().removed)
            assertEquals(0, viewModel.uiState.value.rememberedCount)

            viewModel.onRestore("dairy")
            advanceUntilIdle()
            assertEquals(false, viewModel.uiState.value.remembered.single().removed)
            verifySuspend(VerifyMode.exactly(1)) { removeMemory("dairy") }
            verifySuspend(VerifyMode.exactly(1)) { restoreMemory("dairy") }
        }

    @Test
    fun `GIVEN a remembered memory WHEN removing it fails THEN the row comes back`() =
        runTest(testDispatcher) {
            everySuspend { getLookedOver("chat-1") } returns sheet
            everySuspend { removeMemory("dairy") } throws ServerOfflineException("offline")
            val viewModel = viewModel()
            viewModel.open("chat-1")
            advanceUntilIdle()

            viewModel.onRemove("dairy")
            advanceUntilIdle()

            assertEquals(false, viewModel.uiState.value.remembered.single().removed)
            assertEquals(MemoryActionFailure.RemoveUnreachable, viewModel.uiState.value.failure)
        }
}
