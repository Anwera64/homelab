package com.homelab.household.data.repository

import co.touchlab.kermit.Severity
import com.homelab.household.data.datasource.local.SessionCacheLocalDataSource
import com.homelab.household.data.datasource.remote.`interface`.SessionRemoteDataSource
import com.homelab.household.data.telemetry.RecordingLogWriter
import com.homelab.household.data.telemetry.RecordingLogWriter.Line
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.ChatStreamEvent
import com.homelab.household.domain.util.runCatchingSafe
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A chat stream that drops and one that is picked up again are logged, and those lines leave the
 * phone — so they say that it happened and nothing of the conversation: no id, no words.
 */
class ChatStreamLogTest {
    private val done = ChatStreamEvent.Done(messageId = "m2", assistantContent = "Hello", agentName = "Assistant")

    private fun repository(
        remote: SessionRemoteDataSource,
        writer: RecordingLogWriter,
    ) = SessionRepositoryImpl(
        remote = remote,
        cache = SessionCacheLocalDataSource(),
        pollDelayMs = 10,
        log = writer.logger("Chat"),
    )

    @Test
    fun `GIVEN a stream that drops mid-answer and is picked up WHEN a turn is sent THEN the drop and the resume are each logged once`() =
        runTest {
            // GIVEN
            val writer = RecordingLogWriter()
            val remote = mock<SessionRemoteDataSource>(MockMode.autofill)
            every { remote.openChatStream(any(), any(), any()) } returns
                flow {
                    emit(ChatStreamEvent.Accepted)
                    emit(ChatStreamEvent.Delta("Hel"))
                    throw ServerOfflineException("Stream dropped at 192.168.1.20")
                }
            every { remote.resumeTurnStream("s-1") } returns flowOf(ChatStreamEvent.Delta("lo"), done)

            // WHEN
            repository(remote, writer).streamChatTurn("s-1", "When is the dentist").toList()

            // THEN
            assertEquals(
                listOf(
                    Line(Severity.Warn, "Chat", "Chat stream dropped"),
                    Line(Severity.Info, "Chat", "Chat stream resumed"),
                ),
                writer.lines,
            )
        }

    @Test
    fun `GIVEN a stream that stops without saying how it ended WHEN a turn is sent THEN the drop is logged`() =
        runTest {
            // GIVEN
            val writer = RecordingLogWriter()
            val remote = mock<SessionRemoteDataSource>(MockMode.autofill)
            every { remote.openChatStream(any(), any(), any()) } returns
                flowOf(ChatStreamEvent.Accepted, ChatStreamEvent.Delta("Hel"))
            every { remote.resumeTurnStream("s-1") } returns flowOf(ChatStreamEvent.Delta("lo"), done)

            // WHEN
            repository(remote, writer).streamChatTurn("s-1", "When is the dentist").toList()

            // THEN
            assertEquals("Chat stream dropped", writer.lines.first().message)
        }

    @Test
    fun `GIVEN a stream that ends on its own terms WHEN a turn is sent THEN nothing is logged`() =
        runTest {
            // GIVEN
            val writer = RecordingLogWriter()
            val remote = mock<SessionRemoteDataSource>(MockMode.autofill)
            every { remote.openChatStream(any(), any(), any()) } returns flowOf(ChatStreamEvent.Delta("Hello"), done)

            // WHEN
            repository(remote, writer).streamChatTurn("s-1", "Say hello").toList()

            // THEN
            assertTrue(writer.lines.isEmpty())
        }

    @Test
    fun `GIVEN a hub that cannot be reached at all WHEN a turn is sent THEN no drop is logged because no stream ever opened`() =
        runTest {
            // GIVEN
            val writer = RecordingLogWriter()
            val remote = mock<SessionRemoteDataSource>(MockMode.autofill)
            every { remote.openChatStream(any(), any(), any()) } returns
                flow { throw ServerOfflineException("Cannot reach the hub") }

            // WHEN
            runCatchingSafe { repository(remote, writer).streamChatTurn("s-1", "Say hello").toList() }

            // THEN
            assertTrue(writer.lines.isEmpty())
        }

    @Test
    fun `GIVEN a dropped stream WHEN it is logged THEN the lines carry nothing of the conversation or the failure`() =
        runTest {
            // GIVEN
            val writer = RecordingLogWriter()
            val remote = mock<SessionRemoteDataSource>(MockMode.autofill)
            every { remote.openChatStream(any(), any(), any()) } returns
                flow {
                    emit(ChatStreamEvent.Delta("Hel"))
                    throw ServerOfflineException("Stream dropped at 192.168.1.20")
                }
            every { remote.resumeTurnStream("s-1") } returns flowOf(ChatStreamEvent.Delta("lo"), done)

            // WHEN
            repository(remote, writer).streamChatTurn("s-1", "When is the dentist").toList()

            // THEN
            assertTrue(writer.lines.all { it.throwable == null })
            val logged = writer.lines.joinToString(" ") { it.message }
            listOf(
                "s-1",
                "dentist",
                "Hel",
                "192.168",
            ).forEach { assertFalse(it in logged, "'$it' was logged: $logged") }
        }
}
