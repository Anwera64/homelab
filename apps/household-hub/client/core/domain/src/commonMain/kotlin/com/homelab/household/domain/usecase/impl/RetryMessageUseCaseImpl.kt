package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.model.ChatStreamEvent
import com.homelab.household.domain.repository.SessionRepository
import com.homelab.household.domain.usecase.RetryMessageUseCase
import kotlinx.coroutines.flow.Flow

class RetryMessageUseCaseImpl(
    private val sessionRepository: SessionRepository,
) : RetryMessageUseCase {
    override suspend operator fun invoke(messageId: String): Flow<ChatStreamEvent> =
        sessionRepository.retryMessage(messageId)
}
