package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.ConversationSession

fun interface ListSessionsUseCase {
    suspend operator fun invoke(): List<ConversationSession>
}
