package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.ChatMessage
import com.homelab.household.domain.model.ConversationSession

fun interface GetSessionUseCase {
    suspend operator fun invoke(sessionId: String): Pair<ConversationSession, List<ChatMessage>>
}
