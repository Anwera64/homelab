package com.homelab.household.data.repository

import com.homelab.household.data.datasource.local.SessionCacheLocalDataSource
import com.homelab.household.data.datasource.remote.`interface`.SessionRemoteDataSource
import com.homelab.household.data.mapper.ChatMessageDataMapper
import com.homelab.household.data.mapper.SessionDataMapper
import com.homelab.household.data.network.TurnGoneException
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
import kotlinx.coroutines.flow.FlowCollector
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

    override fun resumeTurn(
        sessionId: String,
        afterAssistantMessageId: String?,
    ): Flow<ChatStreamEvent> = recoverReply(sessionId, afterAssistantMessageId, resumable = true)

    private fun recoverable(
        turn: Flow<ChatStreamEvent>,
        sessionId: String,
        afterAssistantMessageId: String?,
    ): Flow<ChatStreamEvent> =
        flow {
            var delivered = false
            var ended = false
            turn
                .catch { cause ->
                    val worthRecovering = cause is SessionConflictException || delivered
                    if (!worthRecovering) throw cause
                    // A conflict is a turn this stream never heard a word of, so there is nothing of
                    // it to resume; only the conversation can say how it went.
                    val resumable = cause !is SessionConflictException
                    emitAll(recoverReply(sessionId, afterAssistantMessageId, resumable))
                    ended = true
                }.collect { event ->
                    when (event) {
                        // The hub saying it has the question is what decides this, not the first
                        // word: a model loading and then thinking can go a minute without one.
                        is ChatStreamEvent.Accepted, is ChatStreamEvent.Delta -> delivered = true

                        // The hub refused the turn before the question was written down, and
                        // said why. Thrown from the collector, which — as above — goes straight
                        // past the recovery to the caller, and that is the point: there is no
                        // answer on its way to wait for, so the words belong under the composer
                        // and the question belongs marked as never sent.
                        is ChatStreamEvent.StreamError -> throw DomainException(event.message)

                        is ChatStreamEvent.Done, is ChatStreamEvent.TurnFailed -> ended = true

                        else -> Unit
                    }
                    emit(event)
                }

            // A stream that stopped without saying how it ended. It used to be taken for a finished
            // turn, which left the screen waiting on a word that was never coming and the composer
            // refusing every later message, since a turn it believes is running blocks one. Nothing
            // here knows what happened, so it goes and asks — and comes back with one of the three
            // endings, every one of which is something a person can read.
            if (!ended) emitAll(recoverReply(sessionId, afterAssistantMessageId, resumable = true))
        }

    /**
     * Get the rest of a turn this phone stopped hearing, and say which kind of ending it had.
     *
     * First choice is to pick the stream up where it stopped: the hub keeps each turn's events
     * for a while, so the phone asks for everything after the last one it received and the answer
     * carries on, word for word, with nothing missed and nothing said twice. [resumable] is false
     * only when there is no stream of this turn to pick up.
     *
     * When the hub has let the turn go, the conversation is read instead. [afterAssistantMessageId]
     * matters there: a conversation that has run for a while already ends in an assistant message,
     * so "the last assistant message" matches the *previous* answer on the first read, with the
     * real one still being written. Waiting for an id that is not that one is the difference
     * between the answer and a stale one.
     *
     * An unreachable hub is waited out, never thrown. It is what a locked phone looks like from
     * here, and the turn it is waiting on is fine — telling someone their answer failed because
     * the screen went dark was the bug. Every way out is an event: [ChatStreamEvent.Done],
     * [ChatStreamEvent.TurnFailed] when the hub says nothing is running and no answer came, or
     * [ChatStreamEvent.StillWorking] past the ceiling, which the screen can resume from later.
     */
    private fun recoverReply(
        sessionId: String,
        afterAssistantMessageId: String?,
        resumable: Boolean,
    ): Flow<ChatStreamEvent> =
        flow {
            // Say so before the first wait: waiting makes no sound of its own, and the phone is
            // sitting on a half-written answer wondering whether anything is still happening.
            emit(ChatStreamEvent.Reconnecting)

            var canResume = resumable
            var wait = pollDelayMs
            var waited = 0L

            while (true) {
                if (canResume) {
                    when (resumeOnce(sessionId)) {
                        Resumed.ENDED -> {
                            return@flow
                        }

                        Resumed.GONE -> {
                            // Nothing left to stream, so the conversation has the answer now.
                            canResume = false
                            continue
                        }

                        // Words arrived before it dropped again, so the hub is there: start the
                        // wait over rather than spending the one that ran while the phone was away.
                        Resumed.PROGRESSED -> {
                            wait = pollDelayMs
                            waited = 0L
                        }

                        Resumed.DROPPED -> {}
                    }
                } else {
                    val ending = readEnding(sessionId, afterAssistantMessageId)
                    if (ending != null) {
                        emit(ending)
                        return@flow
                    }
                }

                if (waited >= MAX_RECOVERY_WAIT_MS) {
                    emit(ChatStreamEvent.StillWorking)
                    return@flow
                }
                delay(wait)
                waited += wait
                wait = (wait * 1.5).toLong()
            }
        }

    private enum class Resumed { ENDED, GONE, PROGRESSED, DROPPED }

    /** One try at the rest of the stream, passing along whatever it says. */
    private suspend fun FlowCollector<ChatStreamEvent>.resumeOnce(sessionId: String): Resumed {
        // A resumed stream that closes without an ending has nothing more to say, which is the
        // same as the hub having let the turn go: the conversation knows the rest.
        var outcome = Resumed.GONE
        var heard = false
        remote
            .resumeTurnStream(sessionId)
            .catch { cause ->
                when (cause) {
                    is TurnGoneException -> outcome = Resumed.GONE
                    is ServerOfflineException -> outcome = if (heard) Resumed.PROGRESSED else Resumed.DROPPED
                    else -> throw cause
                }
            }.collect { event ->
                heard = true
                when (event) {
                    is ChatStreamEvent.StreamError -> throw DomainException(event.message)
                    is ChatStreamEvent.Done, is ChatStreamEvent.TurnFailed -> outcome = Resumed.ENDED
                    else -> Unit
                }
                emit(event)
            }
        return outcome
    }

    /** The ending the conversation shows, or null while it shows none yet. */
    private suspend fun readEnding(
        sessionId: String,
        afterAssistantMessageId: String?,
    ): ChatStreamEvent? {
        val detail = runCatchingSafe { remote.fetchSession(sessionId) }.getOrNull() ?: return null
        val reply =
            detail.messages
                .lastOrNull { it.role.equals("assistant", ignoreCase = true) }
                ?.takeIf { it.id != afterAssistantMessageId }
        return when {
            reply != null -> {
                ChatStreamEvent.Done(
                    messageId = reply.id,
                    assistantContent = reply.content,
                    agentName = "Assistant",
                )
            }

            !detail.turn_running -> {
                ChatStreamEvent.TurnFailed
            }

            else -> {
                null
            }
        }
    }

    private fun ConversationSession.withLockState(): ConversationSession =
        if (isSecret && cache.isLocked(id)) copy(isSecretLocked = true) else this

    private companion object {
        const val MAX_RECOVERY_WAIT_MS = 60_000L
    }
}
