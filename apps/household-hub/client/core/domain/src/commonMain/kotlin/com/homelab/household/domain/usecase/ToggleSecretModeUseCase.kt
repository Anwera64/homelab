package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.ConversationSession

fun interface ToggleSecretModeUseCase {
    suspend operator fun invoke(
        sessionId: String,
        isSecret: Boolean,
    ): ConversationSession
}
