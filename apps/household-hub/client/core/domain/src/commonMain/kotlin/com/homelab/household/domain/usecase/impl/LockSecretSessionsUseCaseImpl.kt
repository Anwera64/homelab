package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.repository.SessionRepository
import com.homelab.household.domain.usecase.LockSecretSessionsUseCase

class LockSecretSessionsUseCaseImpl(
    private val sessionRepository: SessionRepository,
) : LockSecretSessionsUseCase {
    override suspend operator fun invoke(): Int = sessionRepository.lockAllSecretSessions()
}
