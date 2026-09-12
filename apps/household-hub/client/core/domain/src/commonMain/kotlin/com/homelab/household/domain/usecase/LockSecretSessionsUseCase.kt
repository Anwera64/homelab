package com.homelab.household.domain.usecase

import com.homelab.household.domain.repository.SessionRepository

class LockSecretSessionsUseCase(private val sessionRepository: SessionRepository) {
    suspend operator fun invoke(): Int = sessionRepository.lockAllSecretSessions()
}
