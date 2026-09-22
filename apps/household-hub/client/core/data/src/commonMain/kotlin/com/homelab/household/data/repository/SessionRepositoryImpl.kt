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

    override suspend fun createSession(
        agentId: String,
        title: String,
        isSecret: Boolean,
    ): ConversationSession = SessionDataMapper.toDomain(remote.createSession(agentId, title, isSecret))

    override suspend fun archiveSession(sessionId: String) = remote.archiveSession(sessionId)

    override suspend fun toggleSecretMode(
        sessionId: String,
        isSecret: Boolean,
    ): ConversationSession = SessionDataMapper.toDomain(remote.toggleSecretMode(sessionId, isSecret))

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
    override suspend fun unlockSecretSession(
        sessionId: String,
        pinOrPassword: String,
    ): Boolean = cache.unlockSecretSession(sessionId)

    override fun observeMessages(sessionId: String): Flow<List<ChatMessage>> =
        cache.observeMessages(sessionId).map { it.map(ChatMessageDataMapper::toDomain) }

    override suspend fun retryMessage(messageId: String): Flow<ChatStreamEvent> {
        val (sessionId, message) =
            cache.sessionHolding(messageId)
                ?: throw DomainException("Message with id $messageId not found to retry")
        return streamChatTurn(sessionId = sessionId, content = message.content)
    }

    /**
     * Sends a turn, and decides what a broken stream means.
     *
     * The distinction that matters is *when* it broke. Nothing delivered — no delta ever arrived —
     * means the question itself failed, and the failure is the caller's to show and to offer again.
     * Once a single word has arrived the question is plainly on the hub, and durable execution
     * means it is still being answered whether or not anyone is listening; the answer is then
     * something to go and fetch, not something to report as broken.
     *
     * A 409 recovers the same way. It means a turn is already running on this conversation — in
     * practice, the one whose stream just died — so waiting for it is the same act.
     *
     * `catch` only sees failures from upstream, so a failure in whoever is collecting still
     * reaches them untouched.
     */
    override fun streamChatTurn(
        sessionId: String,
        content: String,
        autoApproveWrites: Boolean,
        afterAssistantMessageId: String?,
    ): Flow<ChatStreamEvent> =
        recoverable(remote.openChatStream(sessionId, content, autoApproveWrites), sessionId, afterAssistantMessageId)

    override fun regenerateTurn(
        sessionId: String,
        afterAssistantMessageId: String?,
    ): Flow<ChatStreamEvent> = recoverable(remote.openRegenerateStream(sessionId), sessionId, afterAssistantMessageId)

    private fun recoverable(
        turn: Flow<ChatStreamEvent>,
        sessionId: String,
        afterAssistantMessageId: String?,
    ): Flow<ChatStreamEvent> =
        flow {
            var delivered = false
            turn
                .catch { cause ->
                    val worthRecovering = cause is SessionConflictException || delivered
                    if (!worthRecovering) throw cause
                    emitAll(recoverReply(sessionId, afterAssistantMessageId))
                }.collect { event ->
                    if (event is ChatStreamEvent.Delta) delivered = true
                    emit(event)
                }
        }

    /**
     * Wait for the answer the hub is still writing, and say which kind of ending this turn had.
     *
     * [afterAssistantMessageId] is the whole trick. A conversation that has run for a while
     * already ends in an assistant message, so "the last assistant message" matches the *previous*
     * answer on the very first read — a second after the stream died, with the real answer still
     * being generated. Waiting for an id that is not that one is the difference between the answer
     * and a stale one.
     *
     * Three ways out, and none of them is an exception. A new answer is [ChatStreamEvent.Done]. A
     * hub that says no turn is running, with still no answer, means the turn died and is worth
     * offering again — [ChatStreamEvent.TurnFailed]. Running past the ceiling is
     * [ChatStreamEvent.StillWorking]: the wait ended, the turn did not, and telling someone their
     * answer failed because we stopped watching would be a lie.
     *
     * A hub that has gone away entirely is still worth saying out loud, so that one throws.
     */
    private fun recoverReply(
        sessionId: String,
        afterAssistantMessageId: String?,
    ): Flow<ChatStreamEvent> =
        flow {
            // Say so before the first wait: polling makes no sound of its own, and the phone is
            // sitting on a half-written answer wondering whether anything is still happening.
            emit(ChatStreamEvent.Reconnecting)

            var wait = pollDelayMs
            var waited = 0L

            while (waited < MAX_RECOVERY_WAIT_MS) {
                delay(wait)
                waited += wait
                wait = (wait * 1.5).toLong()

                val detail =
                    runCatchingSafe { remote.fetchSession(sessionId) }
                        .onFailure { failure ->
                            if (failure is ServerOfflineException) {
                                throw ServerOfflineException(
                                    message = "Server connection lost while polling",
                                    cause = failure,
                                )
                            }
                        }.getOrNull()

                val reply =
                    detail
                        ?.messages
                        ?.lastOrNull { it.role.equals("assistant", ignoreCase = true) }
                        ?.takeIf { it.id != afterAssistantMessageId }

                if (reply != null) {
                    emit(
                        ChatStreamEvent.Done(
                            messageId = reply.id,
                            assistantContent = reply.content,
                            agentName = "Assistant",
                        ),
                    )
                    return@flow
                }

                if (detail != null && !detail.turn_running) {
                    emit(ChatStreamEvent.TurnFailed)
                    return@flow
                }
            }

            emit(ChatStreamEvent.StillWorking)
        }

    private fun ConversationSession.withLockState(): ConversationSession =
        if (isSecret && cache.isLocked(id)) copy(isSecretLocked = true) else this

    private companion object {
        const val MAX_RECOVERY_WAIT_MS = 60_000L
    }
}
