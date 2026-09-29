package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.model.ChatStreamEvent
import com.homelab.household.domain.model.ProposalDetails
import com.homelab.household.domain.repository.SessionRepository
import com.homelab.household.domain.usecase.DecideToolProposalUseCase
import kotlinx.coroutines.flow.Flow

class DecideToolProposalUseCaseImpl(
    private val sessionRepository: SessionRepository,
) : DecideToolProposalUseCase {
    override operator fun invoke(
        sessionId: String,
        toolCallId: String,
        approved: Boolean,
        edited: ProposalDetails?,
    ): Flow<ChatStreamEvent> {
        if (sessionId.isBlank()) throw ValidationException("Session ID cannot be blank")
        if (toolCallId.isBlank()) throw ValidationException("Tool call ID cannot be blank")
        return sessionRepository.decideToolProposal(sessionId, toolCallId, approved, edited)
    }
}
