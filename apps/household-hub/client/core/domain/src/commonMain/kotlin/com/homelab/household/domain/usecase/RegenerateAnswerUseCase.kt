package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.ChatStreamEvent
import kotlinx.coroutines.flow.Flow

/**
 * Asks the agent to answer the last question again, without asking it again.
 *
 * For the one failure where the question arrived and the answer did not. Re-sending the question
 * would store it twice and make the transcript stutter; the screen promises only a fresh answer.
 */
interface RegenerateAnswerUseCase {
    operator fun invoke(
        sessionId: String,
        afterAssistantMessageId: String? = null,
    ): Flow<ChatStreamEvent>
}
