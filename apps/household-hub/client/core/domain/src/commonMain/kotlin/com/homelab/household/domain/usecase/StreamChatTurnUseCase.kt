package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.ChatStreamEvent
import kotlinx.coroutines.flow.Flow

interface StreamChatTurnUseCase {
    /**
     * [afterAssistantMessageId] is the newest answer the caller already has on screen, so a
     * recovery after a dropped stream can tell a new answer from the one already there.
     */
    operator fun invoke(
        sessionId: String,
        content: String,
        autoApproveWrites: Boolean = false,
        afterAssistantMessageId: String? = null,
    ): Flow<ChatStreamEvent>
}
