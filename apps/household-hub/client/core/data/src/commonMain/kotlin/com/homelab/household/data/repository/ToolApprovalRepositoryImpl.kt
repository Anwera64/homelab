package com.homelab.household.data.repository

import com.homelab.household.data.datasource.remote.`interface`.ToolApprovalRemoteDataSource
import com.homelab.household.data.dto.ToolApprovalUpdateDto
import com.homelab.household.data.mapper.ToolApprovalDataMapper
import com.homelab.household.domain.model.ToolAction
import com.homelab.household.domain.model.ToolApproval
import com.homelab.household.domain.repository.ToolApprovalRepository

class ToolApprovalRepositoryImpl(
    private val remote: ToolApprovalRemoteDataSource,
) : ToolApprovalRepository {
    override suspend fun getToolApprovals(): List<ToolApproval> =
        remote.getToolApprovals().mapNotNull(ToolApprovalDataMapper::toDomain)

    override suspend fun setToolApproval(
        tool: String,
        action: ToolAction,
        automatic: Boolean,
    ): List<ToolApproval> =
        remote
            .setToolApproval(ToolApprovalUpdateDto(tool, ToolApprovalDataMapper.toWire(action), automatic))
            .mapNotNull(ToolApprovalDataMapper::toDomain)
}
