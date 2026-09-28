package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.ToolAction
import com.homelab.household.domain.model.ToolApproval

/**
 * Lets agents do one write for the member without asking, or makes it ask again: a card's
 * checkbox, its Undo, or the settings screen. Removing and replacing always ask; the hub refuses them.
 */
fun interface SetToolApprovalUseCase {
    suspend operator fun invoke(
        tool: String,
        action: ToolAction,
        automatic: Boolean,
    ): List<ToolApproval>
}
