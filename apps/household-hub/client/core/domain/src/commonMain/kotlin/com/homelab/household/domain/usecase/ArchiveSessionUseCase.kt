package com.homelab.household.domain.usecase

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.repository.SessionRepository

class ArchiveSessionUseCase(private val sessionRepository: SessionRepository) {
    suspend operator fun invoke(sessionId: String) {
        if (sessionId.isBlank()) throw ValidationException("Session ID cannot be blank")
        sessionRepository.archiveSession(sessionId)
    }
}
