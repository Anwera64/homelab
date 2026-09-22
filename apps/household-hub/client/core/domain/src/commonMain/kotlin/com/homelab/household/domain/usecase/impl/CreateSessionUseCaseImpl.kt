package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.model.ConversationSession
import com.homelab.household.domain.repository.SessionRepository
import com.homelab.household.domain.usecase.CreateSessionUseCase

class CreateSessionUseCaseImpl(
    private val sessionRepository: SessionRepository,
) : CreateSessionUseCase {
    override suspend operator fun invoke(
        agentId: String,
        title: String,
        isSecret: Boolean,
    ): ConversationSession {
        if (agentId.isBlank()) {
            throw ValidationException("Agent ID cannot be blank")
        }
        return sessionRepository.createSession(agentId.trim(), title.trim(), isSecret)
    }
}
