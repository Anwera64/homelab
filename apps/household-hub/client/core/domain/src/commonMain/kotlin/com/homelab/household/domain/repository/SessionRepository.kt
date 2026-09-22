package com.homelab.household.domain.repository

import com.homelab.household.domain.model.ChatMessage
import com.homelab.household.domain.model.ChatStreamEvent
import com.homelab.household.domain.model.ConversationSession
import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    suspend fun listSessions(): List<ConversationSession>

    suspend fun getSession(sessionId: String): Pair<ConversationSession, List<ChatMessage>>

    suspend fun createSession(
        agentId: String,
        title: String = "New Conversation",
        isSecret: Boolean = false,
    ): ConversationSession

    suspend fun archiveSession(sessionId: String)

    suspend fun toggleSecretMode(
        sessionId: String,
        isSecret: Boolean,
    ): ConversationSession

    suspend fun deleteSession(sessionId: String)

    /**
     * Sends a turn and follows it to an end, whatever kind of end it turns out to be.
     *
     * [afterAssistantMessageId] is the newest assistant message the caller already has, and it is
     * what makes recovery possible. When a stream dies part-way the hub keeps generating, so the
     * answer has to be fetched afterwards — but the transcript already holds the *previous*
     * answer, and "the last assistant message" matches that one immediately. Polling for an id
     * that is not this one is the difference between waiting for the real answer and handing back
     * something the person read ten minutes ago. Null means the conversation has no answers yet,
     * so any assistant message is new.
     */
    fun streamChatTurn(
        sessionId: String,
        content: String,
        autoApproveWrites: Boolean = false,
        afterAssistantMessageId: String? = null,
    ): Flow<ChatStreamEvent>

    /**
     * Asks for a fresh answer to the question already in the conversation, without asking it again.
     */
    fun regenerateTurn(
        sessionId: String,
        afterAssistantMessageId: String? = null,
    ): Flow<ChatStreamEvent>

    suspend fun approveToolProposal(
        sessionId: String,
        toolCallId: String,
        approved: Boolean,
        modifiedArguments: Map<String, Any?>? = null,
    ): Boolean

    suspend fun lockAllSecretSessions(): Int

    /**
     * Opens a locked secret conversation, answering whether this caller is the one that opened it.
     *
     * **[pinOrPassword] is not verified yet.** Nothing on either side checks it: the hub serves a
     * secret session's content to any valid token, so the lock is a UI convention and the client
     * was never a trust boundary. `docs/STAGE_5_SECRET_SESSION_LOCKING.md` §1 says so and §4 fixes
     * it server-side; the parameter is here because §5 of that spec has this call send the PIN to
     * `POST /auth/unlock-secret` and hold the `secret_read` token it returns. Until stage 5 slice 5
     * lands, only [UnlockSecretSessionUseCase]'s "you typed something" check stands between a
     * person and a secret conversation.
     */
    suspend fun unlockSecretSession(
        sessionId: String,
        pinOrPassword: String,
    ): Boolean

    fun observeMessages(sessionId: String): Flow<List<ChatMessage>>

    suspend fun retryMessage(messageId: String): Flow<ChatStreamEvent>
}
