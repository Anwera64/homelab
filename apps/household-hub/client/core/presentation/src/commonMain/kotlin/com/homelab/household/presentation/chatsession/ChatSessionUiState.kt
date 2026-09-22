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
    /**
     * What is typed but not yet sent.
     *
     * It lives here rather than in the composer because only this knows when a message has really
     * gone. The screen used to clear it the moment Send was tapped, so a send that failed took the
     * message with it and looked like nothing had happened at all — and nothing typed is ever
     * cleared (design notes §2).
     */
    val composerText: String = "",
    val turnState: TurnState = TurnState.Idle,
    /**
     * Who is answering. Held on the state rather than read off the session, because a chat that
     * has not been created yet still has an agent to name — the hero greeting is drawn before
     * anything exists on the hub.
     */
    val agentName: String = "",
    val agentAvatar: String = "",
    val agentTagline: String = "",
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

    /** A conversation nobody has spoken in yet: the hero greeting rather than a transcript. */
    val isNew: Boolean
        get() = messages.isEmpty() && streamingMessage == null

    /** The newest answer already on screen, which a recovery must not mistake for a new one. */
    val lastAssistantMessageId: String?
        get() = messages.lastOrNull { it.role == MessageRole.ASSISTANT }?.id
}
