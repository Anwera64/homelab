package com.homelab.household.data.repository

import app.cash.turbine.test
import com.homelab.household.data.datasource.local.SessionCacheLocalDataSource
import com.homelab.household.data.datasource.remote.`interface`.SessionRemoteDataSource
import com.homelab.household.data.dto.ChatMessageReadDto
import com.homelab.household.data.dto.SessionDetailReadDto
import com.homelab.household.data.dto.SessionReadDto
import com.homelab.household.domain.exception.DomainException
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.exception.SessionConflictException
import com.homelab.household.domain.model.ChatStreamEvent
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What the repository decides, with the hub and the cache both mocked: which conversations read as
 * locked, what is remembered after a fetch, and — the one real policy in the module — what to do
 * when the hub says it is already working on this conversation.
 *
 * The recovery poll used to be untestable without an engine and a real second of waiting. Nothing
 * here crosses a dispatcher, so `delay` runs on `runTest`'s virtual clock and the whole 60-second
 * timeout is checked in no time at all.
 */
class SessionRepositoryTest {
    private fun sessionDto(
        id: String,
        isSecret: Boolean = false,
        title: String = "Dinner",
    ) = SessionReadDto(id = id, user_id = "u-1", title = title, is_secret = isSecret)

    private fun messageDto(
        id: String,
        sessionId: String,
        role: String,
        content: String,
    ) = ChatMessageReadDto(id = id, session_id = sessionId, role = role, content = content)

    private fun detailDto(
        id: String,
        messages: List<ChatMessageReadDto>,
        isSecret: Boolean = false,
        turnRunning: Boolean = false,
    ) = SessionDetailReadDto(
        id = id,
        user_id = "u-1",
        title = "Kitchen Planning",
        is_secret = isSecret,
        messages = messages,
        turn_running = turnRunning,
    )

    private fun repository(
        remote: SessionRemoteDataSource,
        cache: SessionCacheLocalDataSource = SessionCacheLocalDataSource(),
        pollDelayMs: Long = 1000L,
    ) = SessionRepositoryImpl(remote = remote, cache = cache, pollDelayMs = pollDelayMs)

    // ---- reading -----------------------------------------------------------

    @Test
    fun `GIVEN a household with an ordinary and a secret conversation WHEN they are listed THEN both are mapped and neither is locked`() =
        runTest {
            // GIVEN
            val remote = mock<SessionRemoteDataSource>()
            everySuspend { remote.listSessions() } returns listOf(sessionDto("s-1"), sessionDto("s-2", isSecret = true))

            // WHEN
            val sessions = repository(remote).listSessions()

            // THEN
            assertEquals(listOf("s-1", "s-2"), sessions.map { it.id })
            assertTrue(sessions.none { it.isSecretLocked })
        }

    @Test
    fun `GIVEN a secret conversation that has been locked WHEN conversations are listed THEN only that one reads as locked`() =
        runTest {
            // GIVEN
            val remote = mock<SessionRemoteDataSource>()
            everySuspend { remote.listSessions() } returns listOf(sessionDto("s-1"), sessionDto("s-2", isSecret = true))
            val cache = SessionCacheLocalDataSource().apply { lockSecretSessions(listOf("s-2")) }

            // WHEN
            val sessions = repository(remote, cache).listSessions()

            // THEN
            assertFalse(sessions.first { it.id == "s-1" }.isSecretLocked)
            assertTrue(sessions.first { it.id == "s-2" }.isSecretLocked)
        }

    @Test
    fun `GIVEN a conversation with two messages WHEN it is opened THEN its messages are mapped and remembered`() =
        runTest {
            // GIVEN
            val remote = mock<SessionRemoteDataSource>()
            everySuspend { remote.fetchSession("s-100") } returns
                detailDto(
                    "s-100",
                    listOf(
                        messageDto("m-1", "s-100", "user", "What's for dinner?"),
                        messageDto("m-2", "s-100", "assistant", "How about pasta?"),
                    ),
                )
            val cache = SessionCacheLocalDataSource()
            val repository = repository(remote, cache)

            // WHEN
            val (session, messages) = repository.getSession("s-100")

            // THEN
            assertEquals("Kitchen Planning", session.title)
            assertEquals(listOf("m-1", "m-2"), messages.map { it.id })
            repository.observeMessages("s-100").test {
                assertEquals(listOf("m-1", "m-2"), awaitItem().map { it.id })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `GIVEN the hub cannot be reached WHEN conversations are listed THEN the failure reaches the caller`() =
        runTest {
            // GIVEN
            val remote = mock<SessionRemoteDataSource>()
            everySuspend { remote.listSessions() } throws ServerOfflineException()

            // WHEN / THEN
            assertFailsWith<ServerOfflineException> { repository(remote).listSessions() }
        }

    // ---- secret locks ------------------------------------------------------

    @Test
    fun `GIVEN two secret conversations among four WHEN everything secret is locked THEN it reports two and only those are locked`() =
        runTest {
            // GIVEN
            val remote = mock<SessionRemoteDataSource>()
            everySuspend { remote.listSessions() } returns
                listOf(
                    sessionDto("s-1"),
                    sessionDto("s-2", isSecret = true),
                    sessionDto("s-3"),
                    sessionDto("s-4", isSecret = true),
                )
            val cache = SessionCacheLocalDataSource()

            // WHEN
            val locked = repository(remote, cache).lockAllSecretSessions()

            // THEN
            assertEquals(2, locked)
            assertTrue(cache.isLocked("s-2"))
            assertTrue(cache.isLocked("s-4"))
            assertFalse(cache.isLocked("s-1"))
        }

    @Test
    fun `GIVEN a locked secret conversation WHEN it is unlocked THEN it opens and says so`() =
        runTest {
            // GIVEN
            val remote = mock<SessionRemoteDataSource>(MockMode.autofill)
            val cache = SessionCacheLocalDataSource().apply { lockSecretSessions(listOf("s-2")) }

            // WHEN
            val unlocked = repository(remote, cache).unlockSecretSession("s-2", "482913")

            // THEN
            assertTrue(unlocked)
            assertFalse(cache.isLocked("s-2"))
        }

    // ---- the streamed turn -------------------------------------------------

    @Test
    fun `GIVEN a hub streaming a reply WHEN a turn is sent THEN the events pass through untouched`() =
        runTest {
            // GIVEN
            val remote = mock<SessionRemoteDataSource>()
            val streamed =
                listOf(
                    ChatStreamEvent.Delta("Working on it..."),
                    ChatStreamEvent.Done(
                        messageId = "m2",
                        assistantContent = "Working on it...",
                        agentName = "Assistant",
                    ),
                )
            every { remote.openChatStream("s-1", "Hello", false) } returns flowOf(*streamed.toTypedArray())

            // WHEN
            val events = repository(remote).streamChatTurn("s-1", "Hello").toList()

            // THEN
            assertEquals(streamed, events)
        }

    /**
     * The hub was already working on this conversation, so the turn could not be streamed. Rather
     * than failing, the repository waits and reads the conversation back until the agent's reply
     * has landed — the one piece of policy that belongs to the repository rather than the wire.
     */
    @Test
    fun `GIVEN the hub busy with this conversation WHEN a turn is sent THEN the reply is recovered by reading the conversation back`() =
        runTest {
            // GIVEN
            val remote = mock<SessionRemoteDataSource>()
            every { remote.openChatStream(any(), any(), any()) } returns flow { throw SessionConflictException() }
            var polls = 0
            everySuspend { remote.fetchSession("s-1") } calls {
                polls++
                if (polls == 1) {
                    // Still generating: without this the poll would rightly call the turn dead.
                    detailDto("s-1", listOf(messageDto("m1", "s-1", "user", "Hello")), turnRunning = true)
                } else {
                    detailDto(
                        "s-1",
                        listOf(
                            messageDto("m1", "s-1", "user", "Hello"),
                            messageDto("m2", "s-1", "assistant", "Recovered response"),
                        ),
                    )
                }
            }

            // WHEN
            val events = repository(remote, pollDelayMs = 10).streamChatTurn("s-1", "Hello").toList()

            // THEN
            val done = events.last() as ChatStreamEvent.Done
            assertEquals("Recovered response", done.assistantContent)
            assertEquals(2, polls)
        }

    @Test
    fun `GIVEN the hub busy and never finishing WHEN a turn is sent THEN the wait ends quietly rather than failing`() =
        runTest {
            // GIVEN
            val remote = mock<SessionRemoteDataSource>()
            every { remote.openChatStream(any(), any(), any()) } returns flow { throw SessionConflictException() }
            everySuspend { remote.fetchSession("s-1") } returns
                detailDto("s-1", listOf(messageDto("m1", "s-1", "user", "Hello")), turnRunning = true)

            // WHEN
            val events = repository(remote, pollDelayMs = 1000).streamChatTurn("s-1", "Hello").toList()

            // THEN — this used to throw. A long answer with tool calls may still be landing, and
            // telling someone their question failed because we stopped watching is a lie.
            assertEquals(ChatStreamEvent.StillWorking, events.last())
        }

    @Test
    fun `GIVEN the hub going offline while a busy conversation is polled WHEN a turn is sent THEN the lost connection is reported`() =
        runTest {
            // GIVEN
            val remote = mock<SessionRemoteDataSource>()
            every { remote.openChatStream(any(), any(), any()) } returns flow { throw SessionConflictException() }
            everySuspend { remote.fetchSession("s-1") } throws ServerOfflineException("Connection refused")

            // WHEN
            val thrown =
                assertFailsWith<ServerOfflineException> {
                    repository(remote, pollDelayMs = 10).streamChatTurn("s-1", "Hello").toList()
                }

            // THEN
            assertTrue(thrown.message!!.contains("polling"))
        }

    @Test
    fun `GIVEN a hub that cannot be reached WHEN a turn is sent THEN the failure reaches the caller rather than starting a recovery`() =
        runTest {
            // GIVEN
            val remote = mock<SessionRemoteDataSource>(MockMode.autofill)
            every { remote.openChatStream(any(), any(), any()) } returns flow { throw ServerOfflineException() }

            // WHEN
            assertFailsWith<ServerOfflineException> { repository(remote).streamChatTurn("s-1", "Hello").toList() }

            // THEN
            verifySuspend(VerifyMode.exactly(0)) { remote.fetchSession(any()) }
        }

    // ---- recovering the right answer ---------------------------------------

    /**
     * The half of recovery that is easy to get wrong.
     *
     * A conversation that has been going a while already ends in an assistant message. "Poll until
     * an assistant message appears" is satisfied by that one on the first read, a second after the
     * stream died, while the real answer is still being written. Every test below exists because
     * the id is what tells the new answer from the old one.
     */
    @Test
    fun `GIVEN a conversation that already has an answer WHEN the stream drops THEN the old answer is not mistaken for the new one`() =
        runTest {
            // GIVEN
            val remote = mock<SessionRemoteDataSource>()
            every { remote.openChatStream(any(), any(), any()) } returns
                flow {
                    emit(ChatStreamEvent.Delta("Three things, in order"))
                    throw ServerOfflineException("Stream dropped")
                }
            var polls = 0
            everySuspend { remote.fetchSession("s-1") } calls {
                polls++
                val messages =
                    mutableListOf(
                        messageDto("m1", "s-1", "user", "Last week's question"),
                        messageDto("m2", "s-1", "assistant", "Last week's answer"),
                        messageDto("m3", "s-1", "user", "What's left before Friday?"),
                    )
                if (polls >= 3) {
                    messages += messageDto("m4", "s-1", "assistant", "The panel review is tomorrow")
                }
                detailDto("s-1", messages, turnRunning = polls < 3)
            }

            // WHEN
            val events =
                repository(remote, pollDelayMs = 10)
                    .streamChatTurn("s-1", "What's left before Friday?", afterAssistantMessageId = "m2")
                    .toList()

            // THEN
            val done = events.last() as ChatStreamEvent.Done
            assertEquals("The panel review is tomorrow", done.assistantContent)
            assertEquals("m4", done.messageId)
        }

    @Test
    fun `GIVEN a conversation with no answers yet WHEN the stream drops THEN the first assistant message is the one`() =
        runTest {
            // GIVEN
            val remote = mock<SessionRemoteDataSource>()
            every { remote.openChatStream(any(), any(), any()) } returns
                flow {
                    emit(ChatStreamEvent.Delta("Well"))
                    throw ServerOfflineException("Stream dropped")
                }
            everySuspend { remote.fetchSession("s-1") } returns
                detailDto(
                    "s-1",
                    listOf(
                        messageDto("m1", "s-1", "user", "Hello"),
                        messageDto("m2", "s-1", "assistant", "Hello yourself"),
                    ),
                )

            // WHEN
            val events =
                repository(remote, pollDelayMs = 10)
                    .streamChatTurn("s-1", "Hello", afterAssistantMessageId = null)
                    .toList()

            // THEN
            assertEquals("Hello yourself", (events.last() as ChatStreamEvent.Done).assistantContent)
        }

    @Test
    fun `GIVEN a stream that dies before a single word arrives WHEN a turn is sent THEN nothing is recovered`() =
        runTest {
            // GIVEN — nothing was delivered, so the question failed rather than the answer.
            val remote = mock<SessionRemoteDataSource>(MockMode.autofill)
            every { remote.openChatStream(any(), any(), any()) } returns
                flow { throw ServerOfflineException("Stream dropped") }

            // WHEN
            assertFailsWith<ServerOfflineException> {
                repository(remote, pollDelayMs = 10).streamChatTurn("s-1", "Hello").toList()
            }

            // THEN
            verifySuspend(VerifyMode.exactly(0)) { remote.fetchSession(any()) }
        }

    @Test
    fun `GIVEN the hub no longer working on the turn and no answer WHEN it is polled THEN the turn is reported failed`() =
        runTest {
            // GIVEN
            val remote = mock<SessionRemoteDataSource>()
            every { remote.openChatStream(any(), any(), any()) } returns
                flow {
                    emit(ChatStreamEvent.Delta("Plan"))
                    throw ServerOfflineException("Stream dropped")
                }
            everySuspend { remote.fetchSession("s-1") } returns
                detailDto(
                    "s-1",
                    listOf(messageDto("m1", "s-1", "user", "Plan meals")),
                    turnRunning = false,
                )

            // WHEN
            val events = repository(remote, pollDelayMs = 10).streamChatTurn("s-1", "Plan meals").toList()

            // THEN — it stops as soon as it knows, rather than waiting out the full minute.
            assertEquals(ChatStreamEvent.TurnFailed, events.last())
        }

    @Test
    fun `GIVEN a turn still running after a minute WHEN it is polled THEN the wait ends but the turn does not`() =
        runTest {
            // GIVEN
            val remote = mock<SessionRemoteDataSource>()
            every { remote.openChatStream(any(), any(), any()) } returns
                flow {
                    emit(ChatStreamEvent.Delta("Three things"))
                    throw ServerOfflineException("Stream dropped")
                }
            everySuspend { remote.fetchSession("s-1") } returns
                detailDto(
                    "s-1",
                    listOf(messageDto("m1", "s-1", "user", "What's left before Friday?")),
                    turnRunning = true,
                )

            // WHEN
            val events =
                repository(remote, pollDelayMs = 1000)
                    .streamChatTurn("s-1", "What's left before Friday?")
                    .toList()

            // THEN — never an exception: giving up on waiting is not the answer failing.
            assertEquals(ChatStreamEvent.StillWorking, events.last())
        }

    @Test
    fun `GIVEN the hub busy with this conversation WHEN a turn is sent THEN it waits for the running turn the same way`() =
        runTest {
            // GIVEN
            val remote = mock<SessionRemoteDataSource>()
            every { remote.openChatStream(any(), any(), any()) } returns flow { throw SessionConflictException() }
            var polls = 0
            everySuspend { remote.fetchSession("s-1") } calls {
                polls++
                val messages =
                    mutableListOf(
                        messageDto("m1", "s-1", "user", "Hello"),
                        messageDto("m2", "s-1", "assistant", "An older answer"),
                    )
                if (polls >= 2) messages += messageDto("m3", "s-1", "assistant", "Recovered response")
                detailDto("s-1", messages, turnRunning = polls < 2)
            }

            // WHEN
            val events =
                repository(remote, pollDelayMs = 10)
                    .streamChatTurn("s-1", "Hello", afterAssistantMessageId = "m2")
                    .toList()

            // THEN
            assertEquals("Recovered response", (events.last() as ChatStreamEvent.Done).assistantContent)
        }

    /**
     * The stream ended tidily and said nothing about how. That used to be the end of it: the flow
     * completed, nobody was told, and the screen waited on a word that was never coming — with the
     * composer refusing every later message, because a turn it thinks is still running blocks one.
     *
     * A turn is only over when it says so. Anything else is worth going and asking about.
     */
    @Test
    fun `GIVEN a stream that stops without saying how it ended WHEN a turn is sent THEN the answer is gone and asked for`() =
        runTest {
            // GIVEN
            val remote = mock<SessionRemoteDataSource>()
            every { remote.openChatStream(any(), any(), any()) } returns flowOf(ChatStreamEvent.Delta("Three thi"))
            everySuspend { remote.fetchSession("s-1") } returns
                detailDto(
                    "s-1",
                    listOf(messageDto("m1", "s-1", "user", "Hello")),
                    turnRunning = false,
                )

            // WHEN
            val events = repository(remote, pollDelayMs = 10).streamChatTurn("s-1", "Hello").toList()

            // THEN
            assertEquals(ChatStreamEvent.Delta("Three thi"), events.first())
            assertEquals(ChatStreamEvent.TurnFailed, events.last())
        }

    @Test
    fun `GIVEN a stream that ends on its own terms WHEN a turn is sent THEN nothing is read back`() =
        runTest {
            // GIVEN — a turn that said Done needs no chasing.
            val remote = mock<SessionRemoteDataSource>(MockMode.autofill)
            every { remote.openChatStream(any(), any(), any()) } returns
                flowOf(
                    ChatStreamEvent.Delta("Done"),
                    ChatStreamEvent.Done(messageId = "m2", assistantContent = "Done", agentName = "Assistant"),
                )

            // WHEN
            repository(remote, pollDelayMs = 10).streamChatTurn("s-1", "Hello").toList()

            // THEN
            verifySuspend(VerifyMode.exactly(0)) { remote.fetchSession(any()) }
        }

    /**
     * The hub refused the turn before the question was written down, and said why. That is the
     * caller's to show and the question's to carry — not something to poll for, because there is
     * no answer on its way to find.
     */
    @Test
    fun `GIVEN the hub refusing a turn before it opens WHEN a turn is sent THEN its reason reaches the caller`() =
        runTest {
            // GIVEN
            val remote = mock<SessionRemoteDataSource>(MockMode.autofill)
            every { remote.openChatStream(any(), any(), any()) } returns
                flowOf(ChatStreamEvent.StreamError("Agent personality not found"))

            // WHEN
            val failure =
                assertFailsWith<DomainException> {
                    repository(remote, pollDelayMs = 10).streamChatTurn("s-1", "Hello").toList()
                }

            // THEN
            assertEquals("Agent personality not found", failure.message)
            verifySuspend(VerifyMode.exactly(0)) { remote.fetchSession(any()) }
        }

    @Test
    fun `GIVEN an answer that failed WHEN it is asked for again THEN the question is not sent again`() =
        runTest {
            // GIVEN
            val remote = mock<SessionRemoteDataSource>(MockMode.autofill)
            every { remote.openRegenerateStream(any()) } returns
                flowOf(ChatStreamEvent.Done(messageId = "m9", assistantContent = "A fresh answer"))

            // WHEN
            val events = repository(remote).regenerateTurn("s-1").toList()

            // THEN
            assertEquals("A fresh answer", (events.last() as ChatStreamEvent.Done).assistantContent)
            verify(VerifyMode.exactly(0)) { remote.openChatStream(any(), any(), any()) }
        }

    // ---- retrying ----------------------------------------------------------

    @Test
    fun `GIVEN a message remembered from an open conversation WHEN it is retried THEN its words are sent again to that conversation`() =
        runTest {
            // GIVEN
            val remote = mock<SessionRemoteDataSource>()
            everySuspend { remote.fetchSession("s-100") } returns
                detailDto("s-100", listOf(messageDto("m-retry", "s-100", "user", "Hello retry")))
            every { remote.openChatStream("s-100", "Hello retry", false) } returns
                flowOf(
                    ChatStreamEvent.Delta("Retried response"),
                    // A turn has to say how it ended; one that stops quietly is read as an answer
                    // gone missing and sent to be asked for again.
                    ChatStreamEvent.Done(
                        messageId = "m-retried",
                        assistantContent = "Retried response",
                        agentName = "Assistant",
                    ),
                )
            val repository = repository(remote)
            repository.getSession("s-100")

            // WHEN
            val events = repository.retryMessage("m-retry").toList()

            // THEN
            assertEquals("Retried response", (events.first() as ChatStreamEvent.Delta).content)
        }

    @Test
    fun `GIVEN a message id from no conversation this phone has open WHEN it is retried THEN it says the message is not there`() =
        runTest {
            // GIVEN
            val remote = mock<SessionRemoteDataSource>(MockMode.autofill)
            every { remote.openChatStream(any(), any(), any()) } returns emptyFlow()

            // WHEN / THEN
            assertFailsWith<DomainException> { repository(remote).retryMessage("m-nowhere") }
        }
}
