package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.ChatStreamEvent
import kotlinx.coroutines.flow.Flow

interface StreamChatTurnUseCase {
    operator fun invoke(
        sessionId: String,
        content: String,
        autoApproveWrites: Boolean = false,
    ): Flow<ChatStreamEvent>
}
