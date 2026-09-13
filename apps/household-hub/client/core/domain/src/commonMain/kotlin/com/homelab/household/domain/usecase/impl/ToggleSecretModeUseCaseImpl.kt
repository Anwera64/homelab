package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.model.ConversationSession
import com.homelab.household.domain.repository.SessionRepository
import com.homelab.household.domain.usecase.ToggleSecretModeUseCase

class ToggleSecretModeUseCaseImpl(private val sessionRepository: SessionRepository) : ToggleSecretModeUseCase {
    override suspend operator fun invoke(sessionId: String, isSecret: Boolean): ConversationSession {
        if (sessionId.isBlank()) throw ValidationException("Session ID cannot be blank")
        return sessionRepository.toggleSecretMode(sessionId, isSecret)
    }
}
