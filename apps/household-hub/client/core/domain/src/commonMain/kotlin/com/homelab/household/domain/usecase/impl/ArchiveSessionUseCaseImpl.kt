package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.repository.SessionRepository
import com.homelab.household.domain.usecase.ArchiveSessionUseCase

class ArchiveSessionUseCaseImpl(private val sessionRepository: SessionRepository) : ArchiveSessionUseCase {
    override suspend operator fun invoke(sessionId: String) {
        if (sessionId.isBlank()) throw ValidationException("Session ID cannot be blank")
        sessionRepository.archiveSession(sessionId)
    }
}
