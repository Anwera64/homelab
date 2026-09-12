package com.homelab.household.domain.usecase

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.model.ConversationSession
import com.homelab.household.domain.repository.SessionRepository

class CreateSessionUseCase(private val sessionRepository: SessionRepository) {
    suspend operator fun invoke(agentId: String, title: String = "New Conversation", isSecret: Boolean = false): ConversationSession {
        if (agentId.isBlank()) {
            throw ValidationException("Agent ID cannot be blank")
        }
        return sessionRepository.createSession(agentId.trim(), title.trim(), isSecret)
    }
}
