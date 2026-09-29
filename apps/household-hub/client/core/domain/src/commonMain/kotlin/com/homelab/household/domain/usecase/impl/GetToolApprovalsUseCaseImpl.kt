package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.model.ToolApproval
import com.homelab.household.domain.repository.ToolApprovalRepository
import com.homelab.household.domain.usecase.GetToolApprovalsUseCase

class GetToolApprovalsUseCaseImpl(
    private val toolApprovalRepository: ToolApprovalRepository,
) : GetToolApprovalsUseCase {
    override suspend operator fun invoke(): List<ToolApproval> = toolApprovalRepository.getToolApprovals()
}
