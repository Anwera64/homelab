package com.homelab.household.app.screens.whenagentsact

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.homelab.household.domain.model.HubTool
import com.homelab.household.domain.model.ToolAction
import com.homelab.household.domain.model.ToolApproval
import com.homelab.household.presentation.whenagentsact.SaveFailure
import com.homelab.household.presentation.whenagentsact.ToolApprovalGroup
import com.homelab.household.presentation.whenagentsact.ToolGroup
import com.homelab.household.presentation.whenagentsact.WhenAgentsActStatus
import com.homelab.household.presentation.whenagentsact.WhenAgentsActUiState

/** "When agents act" in each state worth drawing. `WhenAgentsActScreenTest` renders them all. */
class WhenAgentsActUiStateProvider : PreviewParameterProvider<WhenAgentsActUiState> {
    private val groups =
        listOf(
            ToolApprovalGroup(
                ToolGroup.Calendar,
                listOf(
                    ToolApproval(HubTool.CALENDAR_WRITE, ToolAction.Create, automatic = true),
                    ToolApproval(HubTool.CALENDAR_WRITE, ToolAction.Update, automatic = false),
                    ToolApproval(HubTool.CALENDAR_WRITE, ToolAction.Delete, automatic = false, alwaysAsks = true),
                ),
            ),
            ToolApprovalGroup(
                ToolGroup.Notes,
                listOf(
                    ToolApproval(HubTool.DOCUMENT_WRITER, ToolAction.Create, automatic = false),
                    ToolApproval(HubTool.DOCUMENT_WRITER, ToolAction.Append, automatic = false),
                    ToolApproval(HubTool.DOCUMENT_WRITER, ToolAction.Replace, automatic = false, alwaysAsks = true),
                ),
            ),
        )

    private val named =
        listOf(
            "Adding events is automatic" to WhenAgentsActUiState(status = WhenAgentsActStatus.Ready, groups = groups),
            "Arriving" to WhenAgentsActUiState(status = WhenAgentsActStatus.Loading),
            "Hub unreachable" to WhenAgentsActUiState(status = WhenAgentsActStatus.Unreachable),
            "A switch that didn't save" to
                WhenAgentsActUiState(
                    status = WhenAgentsActStatus.Ready,
                    groups = groups,
                    saveFailure = SaveFailure.Unreachable,
                ),
        )

    override val values: Sequence<WhenAgentsActUiState> = named.map { it.second }.asSequence()

    override fun getDisplayName(index: Int): String = named[index].first
}
