package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.ConversationSession
import com.homelab.household.domain.repository.SessionRepository

class ListSessionsUseCase(private val sessionRepository: SessionRepository) {
    suspend operator fun invoke(): List<ConversationSession> = sessionRepository.listSessions()
}
