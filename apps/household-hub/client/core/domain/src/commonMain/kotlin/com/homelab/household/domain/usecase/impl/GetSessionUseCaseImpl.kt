package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.model.ChatMessage
import com.homelab.household.domain.model.ConversationSession
import com.homelab.household.domain.repository.SessionRepository
import com.homelab.household.domain.usecase.GetSessionUseCase

class GetSessionUseCaseImpl(private val sessionRepository: SessionRepository) : GetSessionUseCase {
    override suspend operator fun invoke(sessionId: String): Pair<ConversationSession, List<ChatMessage>> {
        if (sessionId.isBlank()) throw ValidationException("Session ID cannot be blank")
        return sessionRepository.getSession(sessionId)
    }
}
