package com.homelab.household.domain.usecase

import com.homelab.household.domain.repository.SessionRepository

class RetryMessageUseCase(private val sessionRepository: SessionRepository) {
    suspend operator fun invoke(messageId: String) = sessionRepository.retryMessage(messageId)
}
