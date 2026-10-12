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
    fun the_calendar_comes_first_then_notes_each_in_the_order_the_board_draws_them() =
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
    fun a_tool_this_phone_does_not_know_is_left_out() =
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
    fun a_hub_that_cannot_be_reached_says_so_and_try_again_asks_once_more() =
        runTest(testDispatcher) {
            everySuspend { getToolApprovals() } throws ServerOfflineException("offline")

            val viewModel = viewModel()
            advanceUntilIdle()
            assertEquals(WhenAgentsActStatus.Unreachable, viewModel.uiState.value.status)

            everySuspend { getToolApprovals() } returns hubList
            viewModel.load()
            advanceUntilIdle()

            assertEquals(WhenAgentsActStatus.Ready, viewModel.uiState.value.status)
            assertEquals(2, viewModel.uiState.value.groups.size)
        }

    @Test
    fun a_hub_that_answers_with_an_error_is_a_failure_not_offline() =
        runTest(testDispatcher) {
            everySuspend { getToolApprovals() } throws IllegalStateException("500")

            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(WhenAgentsActStatus.Failed, viewModel.uiState.value.status)
        }

    @Test
    fun switching_a_write_on_flips_it_at_once_and_keeps_what_the_hub_answers() =
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
    fun a_switch_the_hub_would_not_save_goes_back_and_says_why() =
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
    fun a_refused_save_is_a_failure_and_the_next_switch_clears_the_line() =
        runTest(testDispatcher) {
            everySuspend { getToolApprovals() } returns hubList
            everySuspend { setToolApproval(any(), any(), any()) } throws IllegalStateException("400")
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onToggle(HubTool.CALENDAR_WRITE, ToolAction.Update, automatic = true)
            advanceUntilIdle()
            assertEquals(SaveFailure.Failed, viewModel.uiState.value.saveFailure)

            everySuspend { setToolApproval(any(), any(), any()) } returns hubList
            viewModel.onToggle(HubTool.CALENDAR_WRITE, ToolAction.Update, automatic = true)
            runCurrent()

            assertNull(viewModel.uiState.value.saveFailure)
        }

    @Test
    fun removing_and_replacing_can_never_be_switched_on() =
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
}
