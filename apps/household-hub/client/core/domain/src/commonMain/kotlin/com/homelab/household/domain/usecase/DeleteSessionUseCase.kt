package com.homelab.household.domain.usecase

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.repository.SessionRepository

class DeleteSessionUseCase(private val sessionRepository: SessionRepository) {
    suspend operator fun invoke(sessionId: String) {
        if (sessionId.isBlank()) throw ValidationException("Session ID cannot be blank")
        sessionRepository.deleteSession(sessionId)
    }
}
