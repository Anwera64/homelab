package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow

fun interface ObserveMessagesUseCase {
    operator fun invoke(sessionId: String): Flow<List<ChatMessage>>
}
