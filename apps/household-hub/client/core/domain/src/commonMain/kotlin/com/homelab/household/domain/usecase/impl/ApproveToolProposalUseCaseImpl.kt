package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.repository.SessionRepository
import com.homelab.household.domain.usecase.ApproveToolProposalUseCase

class ApproveToolProposalUseCaseImpl(
    private val sessionRepository: SessionRepository,
) : ApproveToolProposalUseCase {
    override suspend operator fun invoke(
        sessionId: String,
        toolCallId: String,
        approved: Boolean,
        modifiedArguments: Map<String, Any?>?,
    ): Boolean {
        if (sessionId.isBlank()) throw ValidationException("Session ID cannot be blank")
        if (toolCallId.isBlank()) throw ValidationException("Tool call ID cannot be blank")
        return sessionRepository.approveToolProposal(sessionId, toolCallId, approved, modifiedArguments)
    }
}
