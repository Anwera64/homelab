package com.homelab.household.data.datasource.local

import com.homelab.household.data.dto.ChatMessageReadDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * What this phone remembers about conversations while the app is running: the messages each one has
 * shown, and which secret ones are currently locked.
 *
 * Both used to be a plain `mutableMapOf` and `mutableSetOf` inside the session repository, written
 * from whichever coroutine happened to be streaming. They are state flows here for two reasons.
 * Their writes are atomic, so concurrent updates cannot drop each other. And [observeMessages] can
 * be read without suspending, which the domain's `observeMessages` requires — one flow over all
 * sessions gives that, where a map of per-session flows needed a lock to create an entry safely.
 */
class SessionCacheLocalDataSource {
    private val messagesBySession = MutableStateFlow<Map<String, List<ChatMessageReadDto>>>(emptyMap())
    private val lockedSecretSessions = MutableStateFlow<Set<String>>(emptySet())

    // ---- messages ----------------------------------------------------------

    fun observeMessages(sessionId: String): Flow<List<ChatMessageReadDto>> =
        messagesBySession.map { it[sessionId].orEmpty() }.distinctUntilChanged()

    fun cacheMessages(
        sessionId: String,
        messages: List<ChatMessageReadDto>,
    ) {
        messagesBySession.update { it + (sessionId to messages) }
    }

    /** The conversation a remembered message belongs to, for retrying it without asking the hub. */
    fun sessionHolding(messageId: String): Pair<String, ChatMessageReadDto>? =
        messagesBySession.value.firstNotNullOfOrNull { (sessionId, messages) ->
            messages.firstOrNull { it.id == messageId }?.let { sessionId to it }
        }

    // ---- secret locks ------------------------------------------------------
    //
    // Provisional, and scheduled for deletion. `docs/STAGE_5_SECRET_SESSION_LOCKING.md` §5 replaces
    // this set with locked-by-default: with no `secret_read` token held, every secret session is
    // locked, and a set of ids is then unnecessary rather than something to persist. It is kept for
    // now because that posture needs the hub endpoint from §4, which stage 5 slice 5 adds. What
    // lives here until then is the behaviour the app already had, made safe to share between
    // coroutines — not a design being committed to.

    fun isLocked(sessionId: String): Boolean = sessionId in lockedSecretSessions.value

    fun lockSecretSessions(sessionIds: Collection<String>) {
        lockedSecretSessions.update { it + sessionIds }
    }

    /**
     * Opens a locked conversation, answering whether this caller is the one that opened it. However
     * many ask at once, exactly one is told yes — which is what the screen uses to decide whether to
     * show the conversation, so handing the same answer to several callers would open it twice.
     */
    fun unlockSecretSession(sessionId: String): Boolean {
        while (true) {
            val locked = lockedSecretSessions.value
            if (sessionId !in locked) return false
            if (lockedSecretSessions.compareAndSet(locked, locked - sessionId)) return true
        }
    }
}
