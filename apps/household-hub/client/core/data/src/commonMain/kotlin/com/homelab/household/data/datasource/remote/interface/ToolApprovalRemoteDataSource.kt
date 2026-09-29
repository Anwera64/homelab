package com.homelab.household.data.datasource.remote.`interface`

import com.homelab.household.data.dto.ToolApprovalDto
import com.homelab.household.data.dto.ToolApprovalUpdateDto

/** The member's auto-approve settings on the hub. `ToolApprovalRepositoryImpl` maps them. */
interface ToolApprovalRemoteDataSource {
    suspend fun getToolApprovals(): List<ToolApprovalDto>

    /** Answers the whole list as it now stands. */
    suspend fun setToolApproval(update: ToolApprovalUpdateDto): List<ToolApprovalDto>
}
