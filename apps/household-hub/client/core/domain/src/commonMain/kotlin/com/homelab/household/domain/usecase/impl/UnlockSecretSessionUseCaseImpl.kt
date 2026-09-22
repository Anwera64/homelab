package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.repository.SessionRepository
import com.homelab.household.domain.usecase.UnlockSecretSessionUseCase

class UnlockSecretSessionUseCaseImpl(
    private val sessionRepository: SessionRepository,
) : UnlockSecretSessionUseCase {
    override suspend operator fun invoke(
        sessionId: String,
        pinOrPassword: String,
    ): Boolean {
        if (sessionId.isBlank()) throw ValidationException("Session ID cannot be blank")
        if (pinOrPassword.isBlank()) throw ValidationException("PIN or password cannot be blank")
        return sessionRepository.unlockSecretSession(sessionId, pinOrPassword)
    }
}
