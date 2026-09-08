package com.homelab.household.domain.usecase

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.model.ChatStreamEvent
import com.homelab.household.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow

class StreamChatTurnUseCase(private val sessionRepository: SessionRepository) {
    operator fun invoke(
        sessionId: String,
        content: String,
        autoApproveWrites: Boolean = false
    ): Flow<ChatStreamEvent> {
        if (sessionId.isBlank()) throw ValidationException("Session ID cannot be blank")
        if (content.isBlank()) throw ValidationException("Message content cannot be blank")
        return sessionRepository.streamChatTurn(sessionId, content.trim(), autoApproveWrites)
    }
}
