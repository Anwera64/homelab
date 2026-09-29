package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.model.ToolAction
import com.homelab.household.domain.model.ToolApproval
import com.homelab.household.domain.repository.ToolApprovalRepository
import com.homelab.household.domain.usecase.SetToolApprovalUseCase

class SetToolApprovalUseCaseImpl(
    private val toolApprovalRepository: ToolApprovalRepository,
) : SetToolApprovalUseCase {
    override suspend operator fun invoke(
        tool: String,
        action: ToolAction,
        automatic: Boolean,
    ): List<ToolApproval> = toolApprovalRepository.setToolApproval(tool, action, automatic)
}
