package com.homelab.household.data.repository

import com.homelab.household.data.datasource.local.SessionCacheLocalDataSource
import com.homelab.household.data.datasource.remote.`interface`.SessionRemoteDataSource
import com.homelab.household.data.mapper.ChatMessageDataMapper
import com.homelab.household.data.mapper.SessionDataMapper
import com.homelab.household.domain.exception.DomainException
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.exception.SessionConflictException
import com.homelab.household.domain.model.ChatMessage
import com.homelab.household.domain.model.ChatStreamEvent
import com.homelab.household.domain.model.ConversationSession
import com.homelab.household.domain.repository.SessionRepository
import com.homelab.household.domain.util.runCatchingSafe
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Orchestration and mapping for conversations, plus the one real policy in the module: what to do
 * when the hub says it is already working on a turn for this conversation.
 *
 * Nothing here crosses a dispatcher — the streamed turn's awkwardness lives entirely in
 * `KtorSessionRemoteDataSource.openChatStream` — so every `delay` below runs on the caller's clock,
 * which under test is `runTest`'s virtual one.
 */
class SessionRepositoryImpl(
    private val remote: SessionRemoteDataSource,
    private val cache: SessionCacheLocalDataSource,
    private val pollDelayMs: Long = 1000L,
) : SessionRepository {

    override suspend fun listSessions(): List<ConversationSession> =
        remote.listSessions().map { SessionDataMapper.toDomain(it).withLockState() }

    override suspend fun getSession(sessionId: String): Pair<ConversationSession, List<ChatMessage>> {
        val detail = remote.fetchSession(sessionId)
        cache.cacheMessages(sessionId, detail.messages)
        return SessionDataMapper.toDomain(detail).withLockState() to
            detail.messages.map(ChatMessageDataMapper::toDomain)
    }

    override suspend fun createSession(agentId: String, title: String, isSecret: Boolean): ConversationSession =
        SessionDataMapper.toDomain(remote.createSession(agentId, title, isSecret))

    override suspend fun archiveSession(sessionId: String) = remote.archiveSession(sessionId)

    override suspend fun toggleSecretMode(sessionId: String, isSecret: Boolean): ConversationSession =
        SessionDataMapper.toDomain(remote.toggleSecretMode(sessionId, isSecret))

    override suspend fun deleteSession(sessionId: String) = remote.deleteSession(sessionId)

    override suspend fun approveToolProposal(
        sessionId: String,
        toolCallId: String,
        approved: Boolean,
        modifiedArguments: Map<String, Any?>?,
    ): Boolean = remote.approveToolProposal(sessionId, toolCallId, approved)

    override suspend fun lockAllSecretSessions(): Int {
        val secret = remote.listSessions().filter { it.is_secret }.map { it.id }
        cache.lockSecretSessions(secret)
        return secret.size
    }

    /**
     * [pinOrPassword] is accepted and dropped. That is deliberate and temporary: stage 5 slice 5
     * sends it to `POST /auth/unlock-secret` and keeps the `secret_read` token the hub returns
     * (`docs/STAGE_5_SECRET_SESSION_LOCKING.md` §5). What it must *not* do is check the PIN here —
     * the old version returned true for any non-blank string, which read like verification and was
     * not.
     */
    override suspend fun unlockSecretSession(sessionId: String, pinOrPassword: String): Boolean =
        cache.unlockSecretSession(sessionId)

    override fun observeMessages(sessionId: String): Flow<List<ChatMessage>> =
        cache.observeMessages(sessionId).map { it.map(ChatMessageDataMapper::toDomain) }

    override suspend fun retryMessage(messageId: String): Flow<ChatStreamEvent> {
        val (sessionId, message) = cache.sessionHolding(messageId)
            ?: throw DomainException("Message with id $messageId not found to retry")
        return streamChatTurn(sessionId = sessionId, content = message.content)
    }

    /**
     * `catch` only sees failures from upstream, so a conflict from the hub starts the recovery while
     * a failure in whoever is collecting still reaches them untouched.
     */
    override fun streamChatTurn(
        sessionId: String,
        content: String,
        autoApproveWrites: Boolean,
    ): Flow<ChatStreamEvent> =
        remote.openChatStream(sessionId, content, autoApproveWrites)
            .catch { cause ->
                if (cause is SessionConflictException) emitAll(recoverReply(sessionId)) else throw cause
            }

    /**
     * The hub was busy, so the turn was never streamed. Read the conversation back, waiting longer
     * each time, until the agent's reply has landed — then report it as the [ChatStreamEvent.Done]
     * the collector was waiting for. A refusal along the way is not the end of the turn and is
     * ignored, but a hub that has gone away is worth saying out loud.
     */
    private fun recoverReply(sessionId: String): Flow<ChatStreamEvent> = flow {
        var wait = pollDelayMs
        var waited = 0L

        while (waited < MAX_RECOVERY_WAIT_MS) {
            delay(wait)
            waited += wait
            wait = (wait * 1.5).toLong()

            val detail = runCatchingSafe { remote.fetchSession(sessionId) }
                .onFailure { failure ->
                    if (failure is ServerOfflineException) {
                        throw ServerOfflineException(message = "Server connection lost while polling", cause = failure)
                    }
                }
                .getOrNull()

            val reply = detail?.messages?.lastOrNull { it.role.equals("assistant", ignoreCase = true) }
            if (reply != null) {
                emit(
                    ChatStreamEvent.Done(
                        messageId = reply.id,
                        assistantContent = reply.content,
                        agentName = "Assistant",
                    )
                )
                return@flow
            }
        }

        throw DomainException("Session inference recovery timed out after ${MAX_RECOVERY_WAIT_MS / 1000}s")
    }

    private fun ConversationSession.withLockState(): ConversationSession =
        if (isSecret && cache.isLocked(id)) copy(isSecretLocked = true) else this

    private companion object {
        const val MAX_RECOVERY_WAIT_MS = 60_000L
    }
}
