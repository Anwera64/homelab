package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.model.ConversationSession
import com.homelab.household.domain.repository.SessionRepository
import com.homelab.household.domain.usecase.ListSessionsUseCase

class ListSessionsUseCaseImpl(private val sessionRepository: SessionRepository) : ListSessionsUseCase {
    override suspend operator fun invoke(): List<ConversationSession> = sessionRepository.listSessions()
}
