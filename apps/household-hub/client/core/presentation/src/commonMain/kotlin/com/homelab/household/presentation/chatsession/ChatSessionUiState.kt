package com.homelab.household.presentation.chatsession

import com.homelab.household.domain.model.ChatMessage
import com.homelab.household.domain.model.ChatStreamEvent
import com.homelab.household.domain.model.ConversationSession
import com.homelab.household.domain.model.MessageRole

/**
 * How the answer being written is going.
 *
 * A turn used to be "streaming or not", which could not express the three ways it ends badly, so
 * every failure was written onto the user's message instead — telling someone their question
 * failed when it had plainly arrived. What broke is the answer, and this is where that lives.
 */
sealed interface TurnState {
    /** Nothing is being answered. The composer is free. */
    data object Idle : TurnState

    /** Words are arriving. */
    data object Streaming : TurnState

    /** The stream died after at least one word; the hub is still writing and we are fetching it. */
    data object Reconnecting : TurnState

    /** Past the active wait. The turn is still running, and leaving the chat is fine. */
    data object StillWorking : TurnState

    /** The question arrived, the answer did not. The one case worth offering to do again. */
    data object Failed : TurnState
}

data class ChatSessionUiState(
    val isLoading: Boolean = false,
    val session: ConversationSession? = null,
    val messages: List<ChatMessage> = emptyList(),
    val streamingMessage: String? = null,
    val turnState: TurnState = TurnState.Idle,
    val pendingToolProposal: ChatStreamEvent.ToolApprovalProposal? = null,
    val errorMessage: String? = null,
    val isSecretLocked: Boolean = false,
) {
    /**
     * Whether the composer will take a message.
     *
     * A send while the hub is working on this conversation answers 409, so the screen says so
     * rather than letting it fail. Not a dimmed control — the composer keeps its contrast and
     * changes what its placeholder says (design notes §2).
     */
    val canSend: Boolean
        get() =
            when (turnState) {
                TurnState.Streaming, TurnState.Reconnecting, TurnState.StillWorking -> false
                TurnState.Idle, TurnState.Failed -> true
            }

    /** The newest answer already on screen, which a recovery must not mistake for a new one. */
    val lastAssistantMessageId: String?
        get() = messages.lastOrNull { it.role == MessageRole.ASSISTANT }?.id
}
