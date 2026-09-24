package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.model.ChatStreamEvent
import com.homelab.household.domain.repository.SessionRepository
import com.homelab.household.domain.usecase.ResumeTurnUseCase
import kotlinx.coroutines.flow.Flow

class ResumeTurnUseCaseImpl(
    private val sessionRepository: SessionRepository,
) : ResumeTurnUseCase {
    override operator fun invoke(
        sessionId: String,
        afterAssistantMessageId: String?,
    ): Flow<ChatStreamEvent> {
        if (sessionId.isBlank()) throw ValidationException("Session ID cannot be blank")
        return sessionRepository.resumeTurn(
            sessionId = sessionId,
            afterAssistantMessageId = afterAssistantMessageId,
        )
    }
}
