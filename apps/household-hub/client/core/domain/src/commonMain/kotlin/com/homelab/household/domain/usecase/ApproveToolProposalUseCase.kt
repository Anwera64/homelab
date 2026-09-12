package com.homelab.household.domain.usecase

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.repository.SessionRepository

class ApproveToolProposalUseCase(private val sessionRepository: SessionRepository) {
    suspend operator fun invoke(
        sessionId: String,
        toolCallId: String,
        approved: Boolean,
        modifiedArguments: Map<String, Any?>? = null
    ): Boolean {
        if (sessionId.isBlank()) throw ValidationException("Session ID cannot be blank")
        if (toolCallId.isBlank()) throw ValidationException("Tool call ID cannot be blank")
        return sessionRepository.approveToolProposal(sessionId, toolCallId, approved, modifiedArguments)
    }
}
