package com.homelab.household.presentation.memories

import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.MemoryItem
import com.homelab.household.domain.model.MemoryOrigin
import com.homelab.household.domain.model.MemoryPage
import com.homelab.household.domain.model.MemoryScope
import com.homelab.household.domain.usecase.GetMemoryPageUseCase
import com.homelab.household.domain.usecase.RemoveMemoryUseCase
import com.homelab.household.domain.usecase.RestoreMemoryUseCase
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** The memory page: both tabs, ✕ with Undo in place, and the states around them. */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class MemoriesViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val getMemoryPage = mock<GetMemoryPageUseCase>()
    private val removeMemory = mock<RemoveMemoryUseCase>()
    private val restoreMemory = mock<RestoreMemoryUseCase>()
    private val now = Instant.parse("2026-10-15T18:00:00Z")
    private val clock =
        object : Clock {
            override fun now() = now
        }

    private fun memory(
        id: String,
        scope: MemoryScope = MemoryScope.PERSONAL,
        mine: Boolean = true,
        expiresAt: Instant? = null,
    ) = MemoryItem(
        id = id,
        content = "Memory $id",
        scope = scope,
        origin = MemoryOrigin.Reflection,
        createdAt = Instant.parse("2026-10-15T09:00:00Z"),
        expiresAt = expiresAt,
        speakerId = if (mine) "emma" else "liam",
        speakerName = if (mine) "Emma" else "Liam",
        isMine = mine,
        agentName = "Coordinator",
        agentAvatar = "🏡",
        canRemove = mine,
    )

    private val page =
        MemoryPage(
            yours = listOf(memory("p1"), memory("p2", expiresAt = Instant.parse("2026-10-17T10:00:00Z"))),
            household =
                listOf(
                    memory("h1", MemoryScope.HOUSEHOLD),
                    memory("h2", MemoryScope.HOUSEHOLD, mine = false),
                    memory("h3", MemoryScope.HOUSEHOLD, mine = false),
                ),
        )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        MemoriesViewModel(getMemoryPage, removeMemory, restoreMemory, deviceTimeZone = { 0 }, clock = clock)

    private fun MemoriesUiState.row(id: String) = (yours + household).first { it.item.id == id }

    @Test
    fun `GIVEN memories on the hub WHEN the page opens THEN it loads into Yours with both counts`() =
        runTest(testDispatcher) {
            everySuspend { getMemoryPage() } returns page

            val viewModel = viewModel()
            assertEquals(MemoriesStatus.Loading, viewModel.uiState.value.status)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(MemoriesStatus.Ready, state.status)
            assertEquals(MemoryTab.Yours, state.tab)
            assertEquals(2, state.yoursCount)
            assertEquals(3, state.householdCount)
            assertEquals(MemoryAge.Today, state.row("p1").age)
            assertNull(state.row("p1").until)
            assertEquals(LocalDay(day = 17, month = 10), state.row("p2").until)
        }

    @Test
    fun `GIVEN the hub can't be reached WHEN the page opens THEN it says so and Try again loads it`() =
        runTest(testDispatcher) {
            everySuspend { getMemoryPage() } throws ServerOfflineException()
            val viewModel = viewModel()
            advanceUntilIdle()
            assertEquals(MemoriesStatus.Unreachable, viewModel.uiState.value.status)

            everySuspend { getMemoryPage() } returns page
            viewModel.load()
            advanceUntilIdle()

            assertEquals(MemoriesStatus.Ready, viewModel.uiState.value.status)
        }

    @Test
    fun `GIVEN any other failure WHEN the page opens THEN it reads as failed`() =
        runTest(testDispatcher) {
            everySuspend { getMemoryPage() } throws IllegalStateException("boom")

            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(MemoriesStatus.Failed, viewModel.uiState.value.status)
        }

    @Test
    fun `GIVEN the page WHEN Household is picked THEN the tab changes`() =
        runTest(testDispatcher) {
            everySuspend { getMemoryPage() } returns page
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onTab(MemoryTab.Household)

            assertEquals(MemoryTab.Household, viewModel.uiState.value.tab)
        }

    @Test
    fun `GIVEN a memory WHEN its X is tapped THEN it turns into Removed at once and the count drops`() =
        runTest(testDispatcher) {
            everySuspend { getMemoryPage() } returns page
            val answer = CompletableDeferred<Unit>()
            everySuspend { removeMemory("p1") } calls { answer.await() }
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onRemove("p1")
            runCurrent()

            assertEquals(true, viewModel.uiState.value.row("p1").removed)
            assertEquals(1, viewModel.uiState.value.yoursCount)
            answer.complete(Unit)
            advanceUntilIdle()
            assertEquals(true, viewModel.uiState.value.row("p1").removed)
            verifySuspend(VerifyMode.exactly(1)) { removeMemory("p1") }
        }

    @Test
    fun `GIVEN the hub won't remove it WHEN X is tapped THEN the memory comes back and the page says why`() =
        runTest(testDispatcher) {
            everySuspend { getMemoryPage() } returns page
            everySuspend { removeMemory("h1") } throws ServerOfflineException()
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onRemove("h1")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(false, state.row("h1").removed)
            assertEquals(3, state.householdCount)
            assertEquals(MemoryActionFailure.RemoveUnreachable, state.failure)
        }

    @Test
    fun `GIVEN a removed memory WHEN Undo is tapped THEN it is back with its count`() =
        runTest(testDispatcher) {
            everySuspend { getMemoryPage() } returns page
            everySuspend { removeMemory("p1") } returns Unit
            everySuspend { restoreMemory("p1") } returns Unit
            val viewModel = viewModel()
            advanceUntilIdle()
            viewModel.onRemove("p1")
            advanceUntilIdle()

            viewModel.onUndo("p1")
            advanceUntilIdle()

            assertEquals(false, viewModel.uiState.value.row("p1").removed)
            assertEquals(2, viewModel.uiState.value.yoursCount)
            verifySuspend(VerifyMode.exactly(1)) { restoreMemory("p1") }
        }

    @Test
    fun `GIVEN the hub won't undo WHEN Undo is tapped THEN the row stays Removed and the page says why`() =
        runTest(testDispatcher) {
            everySuspend { getMemoryPage() } returns page
            everySuspend { removeMemory("p1") } returns Unit
            everySuspend { restoreMemory("p1") } throws IllegalStateException("409")
            val viewModel = viewModel()
            advanceUntilIdle()
            viewModel.onRemove("p1")
            advanceUntilIdle()

            viewModel.onUndo("p1")
            advanceUntilIdle()

            assertEquals(true, viewModel.uiState.value.row("p1").removed)
            assertEquals(MemoryActionFailure.UndoFailed, viewModel.uiState.value.failure)
        }

    @Test
    fun `GIVEN a failure shown WHEN the next X is tapped THEN the line goes`() =
        runTest(testDispatcher) {
            everySuspend { getMemoryPage() } returns page
            everySuspend { removeMemory("p1") } throws IllegalStateException()
            everySuspend { removeMemory("p2") } returns Unit
            val viewModel = viewModel()
            advanceUntilIdle()
            viewModel.onRemove("p1")
            advanceUntilIdle()
            assertEquals(MemoryActionFailure.RemoveFailed, viewModel.uiState.value.failure)

            viewModel.onRemove("p2")

            assertNull(viewModel.uiState.value.failure)
        }

    @Test
    fun `GIVEN a memory someone else said WHEN X is asked for THEN nothing happens`() =
        runTest(testDispatcher) {
            everySuspend { getMemoryPage() } returns page
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onRemove("h2")
            advanceUntilIdle()

            assertEquals(false, viewModel.uiState.value.row("h2").removed)
            verifySuspend(VerifyMode.not) { removeMemory("h2") }
        }

    @Test
    fun `GIVEN a removed memory WHEN the page loads again THEN it is gone for good`() =
        runTest(testDispatcher) {
            everySuspend { getMemoryPage() } returns page
            everySuspend { removeMemory("p1") } returns Unit
            val viewModel = viewModel()
            advanceUntilIdle()
            viewModel.onRemove("p1")
            advanceUntilIdle()

            everySuspend { getMemoryPage() } returns page.copy(yours = page.yours.drop(1))
            viewModel.load()
            advanceUntilIdle()

            assertEquals(listOf("p2"), viewModel.uiState.value.yours.map { it.item.id })
        }

    @Test
    fun `GIVEN changes waiting WHEN the page loads THEN the band counts them, and coming back recounts`() =
        runTest(testDispatcher) {
            everySuspend { getMemoryPage() } returns page.copy(toCheck = 2)
            val viewModel = viewModel()
            advanceUntilIdle()
            assertEquals(2, viewModel.uiState.value.toCheck)

            everySuspend { getMemoryPage() } returns page.copy(toCheck = 0)
            viewModel.refreshToCheck()
            advanceUntilIdle()

            assertEquals(0, viewModel.uiState.value.toCheck)
            assertEquals(MemoriesStatus.Ready, viewModel.uiState.value.status)
        }
}
