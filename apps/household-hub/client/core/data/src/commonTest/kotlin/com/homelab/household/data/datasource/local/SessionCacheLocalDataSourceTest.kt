package com.homelab.household.data.datasource.local

import app.cash.turbine.test
import com.homelab.household.data.dto.ChatMessageReadDto
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The state the chat screens share between coroutines, in one place small enough to read at once.
 * It used to be two plain mutable collections inside a 266-line repository, mutated from whichever
 * coroutine happened to be streaming — which is what these tests exist to make impossible.
 */
class SessionCacheLocalDataSourceTest {
    private fun message(
        id: String,
        sessionId: String,
        content: String = "Hello",
    ) = ChatMessageReadDto(id = id, session_id = sessionId, role = "user", content = content)

    // ---- messages ----------------------------------------------------------

    @Test
    fun `GIVEN nothing cached for a session WHEN its messages are observed THEN the conversation reads as empty`() =
        runTest {
            // GIVEN
            val cache = SessionCacheLocalDataSource()

            // WHEN
            val messages = cache.observeMessages("s-1")

            // THEN
            messages.test {
                assertEquals(emptyList(), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `GIVEN a session being observed WHEN its messages are cached THEN the observer sees them`() =
        runTest {
            // GIVEN
            val cache = SessionCacheLocalDataSource()

            // WHEN / THEN
            cache.observeMessages("s-1").test {
                assertEquals(emptyList(), awaitItem())

                cache.cacheMessages("s-1", listOf(message("m-1", "s-1")))

                assertEquals(listOf("m-1"), awaitItem().map { it.id })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `GIVEN two sessions cached WHEN one is observed THEN the other session's messages never reach it`() =
        runTest {
            // GIVEN
            val cache = SessionCacheLocalDataSource()
            cache.cacheMessages("s-1", listOf(message("m-1", "s-1")))

            // WHEN
            cache.cacheMessages("s-2", listOf(message("m-2", "s-2")))

            // THEN
            cache.observeMessages("s-1").test {
                assertEquals(listOf("m-1"), awaitItem().map { it.id })
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `GIVEN a message cached against a session WHEN it is looked up by id THEN the session holding it is found`() =
        runTest {
            // GIVEN
            val cache = SessionCacheLocalDataSource()
            cache.cacheMessages("s-7", listOf(message("m-9", "s-7", content = "What's for dinner?")))

            // WHEN
            val found = cache.sessionHolding("m-9")

            // THEN
            assertEquals("s-7", found?.first)
            assertEquals("What's for dinner?", found?.second?.content)
        }

    @Test
    fun `GIVEN a message id nothing has cached WHEN it is looked up THEN nothing is found`() =
        runTest {
            // GIVEN
            val cache = SessionCacheLocalDataSource()
            cache.cacheMessages("s-1", listOf(message("m-1", "s-1")))

            // WHEN
            val found = cache.sessionHolding("m-does-not-exist")

            // THEN
            assertNull(found)
        }

    /**
     * Twenty sessions cached at once from twenty coroutines. Against the `mutableMapOf` this
     * replaced, a concurrent write could drop an entry; the point of the state flow is that this
     * cannot happen, and the point of this test is that nobody quietly puts the map back.
     */
    @Test
    fun `GIVEN many sessions cached at the same time WHEN every write has landed THEN none of them was lost`() =
        runTest {
            // GIVEN
            val cache = SessionCacheLocalDataSource()
            val sessionIds = (1..20).map { "s-$it" }

            // WHEN
            sessionIds.map { id -> async { cache.cacheMessages(id, listOf(message("m-$id", id))) } }.awaitAll()

            // THEN
            assertEquals(sessionIds.toSet(), sessionIds.filter { cache.sessionHolding("m-$it") != null }.toSet())
        }

    // ---- secret locks ------------------------------------------------------
    //
    // These pin current behaviour, not intended behaviour. Stage 5 slice 5 makes secret sessions
    // locked-by-default against a held `secret_read` token and deletes the set underneath these
    // tests (`docs/STAGE_5_SECRET_SESSION_LOCKING.md` §5). Deleting them along with it is the plan,
    // not a regression.

    @Test
    fun `GIVEN a household with no locks WHEN a session is asked about THEN it is not locked`() =
        runTest {
            // GIVEN
            val cache = SessionCacheLocalDataSource()

            // WHEN
            val locked = cache.isLocked("s-1")

            // THEN
            assertFalse(locked)
        }

    @Test
    fun `GIVEN two secret sessions WHEN they are locked THEN both read as locked and others do not`() =
        runTest {
            // GIVEN
            val cache = SessionCacheLocalDataSource()

            // WHEN
            cache.lockSecretSessions(listOf("s-1", "s-2"))

            // THEN
            assertTrue(cache.isLocked("s-1"))
            assertTrue(cache.isLocked("s-2"))
            assertFalse(cache.isLocked("s-3"))
        }

    @Test
    fun `GIVEN a locked session WHEN it is unlocked THEN it says it did and the session is open`() =
        runTest {
            // GIVEN
            val cache = SessionCacheLocalDataSource()
            cache.lockSecretSessions(listOf("s-1"))

            // WHEN
            val unlocked = cache.unlockSecretSession("s-1")

            // THEN
            assertTrue(unlocked)
            assertFalse(cache.isLocked("s-1"))
        }

    @Test
    fun `GIVEN a session that was never locked WHEN it is unlocked THEN it says there was nothing to unlock`() =
        runTest {
            // GIVEN
            val cache = SessionCacheLocalDataSource()

            // WHEN
            val unlocked = cache.unlockSecretSession("s-1")

            // THEN
            assertFalse(unlocked)
        }

    /**
     * Only one caller may be told it did the unlocking, however many ask at once — that answer is
     * what the UI uses to decide whether to open the conversation.
     */
    @Test
    fun `GIVEN one locked session WHEN five callers unlock it together THEN exactly one of them did it`() =
        runTest {
            // GIVEN
            val cache = SessionCacheLocalDataSource()
            cache.lockSecretSessions(listOf("s-1"))

            // WHEN
            val outcomes = (1..5).map { async { cache.unlockSecretSession("s-1") } }.awaitAll()

            // THEN
            assertEquals(1, outcomes.count { it })
            assertFalse(cache.isLocked("s-1"))
        }
}
