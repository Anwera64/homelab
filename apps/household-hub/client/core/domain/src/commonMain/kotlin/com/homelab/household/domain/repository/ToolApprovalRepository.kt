package com.homelab.household.domain.repository

import com.homelab.household.domain.model.ToolAction
import com.homelab.household.domain.model.ToolApproval

/** Which writes agents do for the member without asking. Each member's choices are their own. */
interface ToolApprovalRepository {
    /** Every write agents can do, each asking until the member makes it automatic. */
    suspend fun getToolApprovals(): List<ToolApproval>

    /** Turns one write automatic or back to asking, and answers the whole list as it now stands. */
    suspend fun setToolApproval(
        tool: String,
        action: ToolAction,
        automatic: Boolean,
    ): List<ToolApproval>
}
