package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.model.ChatMessage
import com.homelab.household.domain.repository.SessionRepository
import com.homelab.household.domain.usecase.ObserveMessagesUseCase
import kotlinx.coroutines.flow.Flow

class ObserveMessagesUseCaseImpl(private val sessionRepository: SessionRepository) : ObserveMessagesUseCase {
    override operator fun invoke(sessionId: String): Flow<List<ChatMessage>> =
        sessionRepository.observeMessages(sessionId)
}
