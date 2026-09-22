package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.ConversationSession

interface CreateSessionUseCase {
    suspend operator fun invoke(
        agentId: String,
        title: String = "New Conversation",
        isSecret: Boolean = false,
    ): ConversationSession
}
