package com.homelab.household.domain.usecase

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.repository.SessionRepository

class LockSecretSessionsUseCase(private val sessionRepository: SessionRepository) {
    suspend operator fun invoke(): Int = sessionRepository.lockAllSecretSessions()
}

class UnlockSecretSessionUseCase(private val sessionRepository: SessionRepository) {
    suspend operator fun invoke(sessionId: String, pinOrPassword: String): Boolean {
        if (sessionId.isBlank()) throw ValidationException("Session ID cannot be blank")
        if (pinOrPassword.isBlank()) throw ValidationException("PIN or password cannot be blank")
        return sessionRepository.unlockSecretSession(sessionId, pinOrPassword)
    }
}
