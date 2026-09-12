package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.ChatMessage
import com.homelab.household.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow

class ObserveMessagesUseCase(private val sessionRepository: SessionRepository) {
    operator fun invoke(sessionId: String): Flow<List<ChatMessage>> = sessionRepository.observeMessages(sessionId)
}
