package com.homelab.household.presentation.whenagentsact

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.HubTool
import com.homelab.household.domain.model.ToolAction
import com.homelab.household.domain.model.ToolApproval
import com.homelab.household.domain.model.alwaysAsks
import com.homelab.household.domain.usecase.GetToolApprovalsUseCase
import com.homelab.household.domain.usecase.SetToolApprovalUseCase
import com.homelab.household.domain.util.runCatchingSafe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * "When agents act" on Profile: which writes agents do without asking. The same settings as the
 * card's "from now on" box, through the same use case, so the two can never disagree.
 */
class WhenAgentsActViewModel(
    private val getToolApprovals: GetToolApprovalsUseCase,
    private val setToolApproval: SetToolApprovalUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WhenAgentsActUiState())
    val uiState: StateFlow<WhenAgentsActUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /** Also the "Try again" when the hub didn't answer. */
    fun load() {
        _uiState.update { it.copy(status = WhenAgentsActStatus.Loading) }
        viewModelScope.launch {
            runCatchingSafe { getToolApprovals() }.fold(
                onSuccess = { approvals ->
                    _uiState.update { it.copy(status = WhenAgentsActStatus.Ready, groups = groupsOf(approvals)) }
                },
                onFailure = { error -> _uiState.update { it.copy(status = failureOf(error)) } },
            )
        }
    }

    /**
     * The switch moves at once; the hub's answer then becomes the whole list. If the hub won't
     * save it, the switch goes back to what the hub still has, and the screen says why.
     */
    fun onToggle(
        tool: String,
        action: ToolAction,
        automatic: Boolean,
    ) {
        if (alwaysAsks(action)) return
        val before = _uiState.value.groups
        _uiState.update { state ->
            state.copy(groups = state.groups.withSwitch(tool, action, automatic), saveFailure = null)
        }
        viewModelScope.launch {
            runCatchingSafe { setToolApproval(tool, action, automatic) }.fold(
                onSuccess = { approvals -> _uiState.update { it.copy(groups = groupsOf(approvals)) } },
                onFailure = { error ->
                    val failure = if (error is ServerOfflineException) SaveFailure.Unreachable else SaveFailure.Failed
                    _uiState.update { it.copy(groups = before, saveFailure = failure) }
                },
            )
        }
    }

    private fun List<ToolApprovalGroup>.withSwitch(
        tool: String,
        action: ToolAction,
        automatic: Boolean,
    ) = map { group ->
        group.copy(
            approvals =
                group.approvals.map {
                    if (it.tool == tool && it.action == action) it.copy(automatic = automatic) else it
                },
        )
    }

    /** One card on the screen: a tool's writes, in the board's order. */
    private class Card(
        val group: ToolGroup,
        val tool: String,
        val order: List<ToolAction>,
    )

    private companion object {
        /** A tool or action not named here isn't one this phone can draw. */
        val CARDS =
            listOf(
                Card(
                    ToolGroup.Calendar,
                    HubTool.CALENDAR_WRITE,
                    listOf(ToolAction.Create, ToolAction.Update, ToolAction.Delete),
                ),
                Card(
                    ToolGroup.Notes,
                    HubTool.DOCUMENT_WRITER,
                    listOf(ToolAction.Create, ToolAction.Append, ToolAction.Replace),
                ),
            )

        fun groupsOf(approvals: List<ToolApproval>): List<ToolApprovalGroup> =
            CARDS.mapNotNull { card ->
                val ordered =
                    card.order.mapNotNull { action ->
                        approvals.firstOrNull { it.tool == card.tool && it.action == action }
                    }
                ordered.takeIf { it.isNotEmpty() }?.let { ToolApprovalGroup(card.group, it) }
            }

        fun failureOf(error: Throwable) =
            if (error is ServerOfflineException) WhenAgentsActStatus.Unreachable else WhenAgentsActStatus.Failed
    }
}
