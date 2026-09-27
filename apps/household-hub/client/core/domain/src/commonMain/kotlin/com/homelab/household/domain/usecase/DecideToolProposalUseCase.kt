package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.ChatStreamEvent
import kotlinx.coroutines.flow.Flow

/**
 * The member's answer to one approval card, and the rest of the turn it paused.
 *
 * While other cards of the same step still wait, the flow ends in [ChatStreamEvent.AwaitingApproval]
 * again; after the last one it carries the answer on to [ChatStreamEvent.Done], as the same message.
 * [modifiedArguments] are details the member changed on the card, over the ones proposed.
 */
interface DecideToolProposalUseCase {
    operator fun invoke(
        sessionId: String,
        toolCallId: String,
        approved: Boolean,
        modifiedArguments: Map<String, Any?>? = null,
    ): Flow<ChatStreamEvent>
}
