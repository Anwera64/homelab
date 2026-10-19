package com.homelab.household.presentation.whenagentsact

import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.HubTool
import com.homelab.household.domain.model.ToolAction
import com.homelab.household.domain.model.ToolApproval
import com.homelab.household.domain.usecase.GetToolApprovalsUseCase
import com.homelab.household.domain.usecase.SetToolApprovalUseCase
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
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

/** Every write an agent can do, and which of them wait for the member's okay. */
@OptIn(ExperimentalCoroutinesApi::class)
class WhenAgentsActViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val getToolApprovals = mock<GetToolApprovalsUseCase>()
    private val setToolApproval = mock<SetToolApprovalUseCase>()

    /** The hub's list, in the hub's own order: not the order the screen draws it in. */
    private val hubList =
        listOf(
            ToolApproval(HubTool.DOCUMENT_WRITER, ToolAction.Replace, automatic = false, alwaysAsks = true),
            ToolApproval(HubTool.CALENDAR_WRITE, ToolAction.Delete, automatic = false, alwaysAsks = true),
            ToolApproval(HubTool.DOCUMENT_WRITER, ToolAction.Append, automatic = false),
            ToolApproval(HubTool.CALENDAR_WRITE, ToolAction.Update, automatic = false),
            ToolApproval(HubTool.DOCUMENT_WRITER, ToolAction.Create, automatic = false),
            ToolApproval(HubTool.CALENDAR_WRITE, ToolAction.Create, automatic = true),
            ToolApproval("garden_sprinkler", ToolAction.Create, automatic = false),
        )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = WhenAgentsActViewModel(getToolApprovals, setToolApproval)

    private fun WhenAgentsActUiState.approval(
        tool: String,
        action: ToolAction,
    ) = groups.flatMap { it.approvals }.first { it.tool == tool && it.action == action }

    @Test
    fun `GIVEN the hub lists calendar and notes approvals WHEN the screen loads THEN the calendar comes first then notes each in the order the board draws them`() =
        runTest(testDispatcher) {
            everySuspend { getToolApprovals() } returns hubList

            val viewModel = viewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(WhenAgentsActStatus.Ready, state.status)
            assertEquals(listOf(ToolGroup.Calendar, ToolGroup.Notes), state.groups.map { it.group })
            assertEquals(
                listOf(ToolAction.Create, ToolAction.Update, ToolAction.Delete),
                state.groups[0].approvals.map { it.action },
            )
            assertEquals(
                listOf(ToolAction.Create, ToolAction.Append, ToolAction.Replace),
                state.groups[1].approvals.map { it.action },
            )
            assertEquals(true, state.approval(HubTool.CALENDAR_WRITE, ToolAction.Create).automatic)
            assertEquals(true, state.approval(HubTool.CALENDAR_WRITE, ToolAction.Delete).alwaysAsks)
            assertEquals(true, state.approval(HubTool.DOCUMENT_WRITER, ToolAction.Replace).alwaysAsks)
        }

    @Test
    fun `GIVEN the hub lists a tool this phone does not know WHEN the screen loads THEN that tool is left out`() =
        runTest(testDispatcher) {
            everySuspend { getToolApprovals() } returns hubList

            val viewModel = viewModel()
            advanceUntilIdle()

            val tools =
                viewModel.uiState.value.groups
                    .flatMap { group -> group.approvals.map { it.tool } }
            assertEquals(setOf(HubTool.CALENDAR_WRITE, HubTool.DOCUMENT_WRITER), tools.toSet())
        }

    @Test
    fun `GIVEN a hub that cannot be reached WHEN the screen loads THEN the status says unreachable`() =
        runTest(testDispatcher) {
            everySuspend { getToolApprovals() } throws ServerOfflineException("offline")
            val viewModel = viewModel()
            advanceUntilIdle()
            assertEquals(WhenAgentsActStatus.Unreachable, viewModel.uiState.value.status)
        }

    @Test
    fun `GIVEN a hub that was unreachable and has since come back WHEN trying again THEN the board is ready with both groups`() =
        runTest(testDispatcher) {
            everySuspend { getToolApprovals() } throws ServerOfflineException("offline")
            val viewModel = viewModel()
            advanceUntilIdle()
            everySuspend { getToolApprovals() } returns hubList
            viewModel.load()
            advanceUntilIdle()

            assertEquals(WhenAgentsActStatus.Ready, viewModel.uiState.value.status)
            assertEquals(2, viewModel.uiState.value.groups.size)
        }

    @Test
    fun `GIVEN a hub that answers with an error WHEN the screen loads THEN the status is failed rather than unreachable`() =
        runTest(testDispatcher) {
            everySuspend { getToolApprovals() } throws IllegalStateException("500")

            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(WhenAgentsActStatus.Failed, viewModel.uiState.value.status)
        }

    @Test
    fun `GIVEN a write that is off WHEN switching it on THEN it flips at once and keeps what the hub answers`() =
        runTest(testDispatcher) {
            everySuspend { getToolApprovals() } returns hubList
            val hubAnswer = CompletableDeferred<List<ToolApproval>>()
            everySuspend { setToolApproval(any(), any(), any()) } calls { hubAnswer.await() }
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onToggle(HubTool.DOCUMENT_WRITER, ToolAction.Create, automatic = true)
            runCurrent()
            assertEquals(
                true,
                viewModel.uiState.value
                    .approval(HubTool.DOCUMENT_WRITER, ToolAction.Create)
                    .automatic,
            )

            hubAnswer.complete(
                hubList.map {
                    if (it.tool == HubTool.DOCUMENT_WRITER &&
                        it.action == ToolAction.Create
                    ) {
                        it.copy(automatic = true)
                    } else {
                        it
                    }
                },
            )
            advanceUntilIdle()

            verifySuspend { setToolApproval(HubTool.DOCUMENT_WRITER, ToolAction.Create, true) }
            val state = viewModel.uiState.value
            assertEquals(true, state.approval(HubTool.DOCUMENT_WRITER, ToolAction.Create).automatic)
            assertEquals(true, state.approval(HubTool.CALENDAR_WRITE, ToolAction.Create).automatic)
            assertNull(state.saveFailure)
        }

    @Test
    fun `GIVEN a hub that cannot be reached WHEN switching a write off THEN the switch goes back and the failure is said beside it`() =
        runTest(testDispatcher) {
            everySuspend { getToolApprovals() } returns hubList
            everySuspend { setToolApproval(any(), any(), any()) } throws ServerOfflineException("offline")
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onToggle(HubTool.CALENDAR_WRITE, ToolAction.Create, automatic = false)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(true, state.approval(HubTool.CALENDAR_WRITE, ToolAction.Create).automatic)
            assertEquals(SaveFailure.Unreachable, state.saveFailure)
            assertEquals(ToolGroup.Calendar, state.saveFailureIn, "said beside the switch that went back")
        }

    @Test
    fun `GIVEN a hub that refuses the save WHEN switching a write on THEN the save failure is failed`() =
        runTest(testDispatcher) {
            everySuspend { getToolApprovals() } returns hubList
            everySuspend { setToolApproval(any(), any(), any()) } throws IllegalStateException("400")
            val viewModel = viewModel()
            advanceUntilIdle()
            viewModel.onToggle(HubTool.CALENDAR_WRITE, ToolAction.Update, automatic = true)
            advanceUntilIdle()
            assertEquals(SaveFailure.Failed, viewModel.uiState.value.saveFailure)
        }

    @Test
    fun `GIVEN a refused save WHEN switching again THEN the failure line clears`() =
        runTest(testDispatcher) {
            everySuspend { getToolApprovals() } returns hubList
            everySuspend { setToolApproval(any(), any(), any()) } throws IllegalStateException("400")
            val viewModel = viewModel()
            advanceUntilIdle()
            viewModel.onToggle(HubTool.CALENDAR_WRITE, ToolAction.Update, automatic = true)
            advanceUntilIdle()
            everySuspend { setToolApproval(any(), any(), any()) } returns hubList
            viewModel.onToggle(HubTool.CALENDAR_WRITE, ToolAction.Update, automatic = true)
            runCurrent()

            assertNull(viewModel.uiState.value.saveFailure)
        }

    @Test
    fun `GIVEN a write that always asks WHEN switching it on THEN nothing is saved and it stays off`() =
        runTest(testDispatcher) {
            everySuspend { getToolApprovals() } returns hubList
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onToggle(HubTool.CALENDAR_WRITE, ToolAction.Delete, automatic = true)
            advanceUntilIdle()

            verifySuspend(VerifyMode.not) { setToolApproval(any(), any(), any()) }
            assertEquals(
                false,
                viewModel.uiState.value
                    .approval(HubTool.CALENDAR_WRITE, ToolAction.Delete)
                    .automatic,
            )
        }

    @Test
    fun `GIVEN the hub lists memory approvals WHEN the screen loads THEN remembering and forgetting come last as your memories`() =
        runTest(testDispatcher) {
            val memories =
                listOf(
                    ToolApproval(HubTool.FORGET, ToolAction.Delete, automatic = false, alwaysAsks = true),
                    ToolApproval(HubTool.REMEMBER, ToolAction.Create, automatic = true),
                )
            everySuspend { getToolApprovals() } returns memories + hubList

            val viewModel = viewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(listOf(ToolGroup.Calendar, ToolGroup.Notes, ToolGroup.Memories), state.groups.map { it.group })
            assertEquals(memories.reversed(), state.groups.last().approvals)
        }

    @Test
    fun `GIVEN forgetting that always asks WHEN switching it on THEN nothing is saved`() =
        runTest(testDispatcher) {
            everySuspend { getToolApprovals() } returns
                listOf(ToolApproval(HubTool.FORGET, ToolAction.Delete, automatic = false, alwaysAsks = true))
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onToggle(HubTool.FORGET, ToolAction.Delete, automatic = true)
            advanceUntilIdle()

            verifySuspend(VerifyMode.not) { setToolApproval(any(), any(), any()) }
        }
}
