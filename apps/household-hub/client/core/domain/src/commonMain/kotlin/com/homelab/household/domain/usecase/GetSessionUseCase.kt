package com.homelab.household.domain.usecase

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.model.ChatMessage
import com.homelab.household.domain.model.ConversationSession
import com.homelab.household.domain.repository.SessionRepository

class GetSessionUseCase(private val sessionRepository: SessionRepository) {
    suspend operator fun invoke(sessionId: String): Pair<ConversationSession, List<ChatMessage>> {
        if (sessionId.isBlank()) throw ValidationException("Session ID cannot be blank")
        return sessionRepository.getSession(sessionId)
    }
}
