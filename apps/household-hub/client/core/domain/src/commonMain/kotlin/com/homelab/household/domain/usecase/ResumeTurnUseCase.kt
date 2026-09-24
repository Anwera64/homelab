package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.ChatStreamEvent
import kotlinx.coroutines.flow.Flow

/**
 * Picks up a turn the phone stopped hearing — the screen locked, the app went to the background —
 * from the last word it received, or reads the saved answer if the hub has let the turn go.
 */
interface ResumeTurnUseCase {
    operator fun invoke(
        sessionId: String,
        afterAssistantMessageId: String? = null,
    ): Flow<ChatStreamEvent>
}
