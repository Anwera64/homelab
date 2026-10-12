package com.homelab.household.presentation.whenagentsact

import com.homelab.household.domain.model.ToolApproval

/** Every write an agent can do for the member, grouped the way the screen draws them. */
data class WhenAgentsActUiState(
    val status: WhenAgentsActStatus = WhenAgentsActStatus.Loading,
    val groups: List<ToolApprovalGroup> = emptyList(),
    /** The last switch the hub wouldn't save. It has already gone back to what the hub has. */
    val saveFailure: SaveFailure? = null,
    /** The card holding that switch, so the line is said beside it rather than at the foot of the page. */
    val saveFailureIn: ToolGroup? = null,
)

enum class WhenAgentsActStatus {
    Loading,
    Ready,
    Unreachable,
    Failed,
}

enum class SaveFailure {
    Unreachable,
    Failed,
}

/** What an agent writes to: one card each on the screen. */
enum class ToolGroup {
    Calendar,
    Notes,
}

data class ToolApprovalGroup(
    val group: ToolGroup,
    val approvals: List<ToolApproval>,
)
