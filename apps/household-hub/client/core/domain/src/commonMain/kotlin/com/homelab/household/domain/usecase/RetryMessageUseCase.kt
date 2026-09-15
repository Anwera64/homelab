package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.ChatStreamEvent
import kotlinx.coroutines.flow.Flow

fun interface RetryMessageUseCase {
    suspend operator fun invoke(messageId: String): Flow<ChatStreamEvent>
}
