package com.homelab.household.data.datasource.remote.sse

import com.homelab.household.domain.model.ChatStreamEvent
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefensiveSseStreamReaderTest {
    private val reader = DefensiveSseStreamReader()

    @Test
    fun parse_sse_stream_decodes_deltas_and_done() =
        runTest {
            val ssePayload =
                """
                data: {"type": "delta", "content": "Hello"}

                data: {"type": "delta", "content": " world"}

                data: {"type": "done", "message_id": "m1", "assistant_content": "Hello world", "suggest_secret_mode": false, "is_turn_secret": false, "agent_name": "Assistant"}

                data: [DONE]

                """.trimIndent()

            val channel = ByteReadChannel(ssePayload.encodeToByteArray())
            val events = reader.readEvents(channel).toList()

            assertEquals(3, events.size)
            assertTrue(events[0] is ChatStreamEvent.Delta)
            assertEquals("Hello", (events[0] as ChatStreamEvent.Delta).content)
            assertTrue(events[1] is ChatStreamEvent.Delta)
            assertEquals(" world", (events[1] as ChatStreamEvent.Delta).content)
            assertTrue(events[2] is ChatStreamEvent.Done)
            assertEquals("Hello world", (events[2] as ChatStreamEvent.Done).assistantContent)
        }

    @Test
    fun parse_sse_stream_strips_control_delimiter_tokens() =
        runTest {
            val ssePayload =
                """
                data: {"type": "delta", "content": "<|im_start|>system\nFiltered content###"}

                data: [DONE]

                """.trimIndent()

            val channel = ByteReadChannel(ssePayload.encodeToByteArray())
            val events = reader.readEvents(channel).toList()

            assertEquals(1, events.size)
            val delta = events[0] as ChatStreamEvent.Delta
            assertTrue(!delta.content.contains("<|im_start|>"))
            assertTrue(!delta.content.contains("###"))
            assertTrue(delta.content.contains("Filtered content"))
        }

    /**
     * A line can be valid JSON and still be shaped wrongly — `"type"` arriving as an object rather
     * than a string. `JsonElement.jsonPrimitive` reports that with `error(...)`, i.e. an
     * IllegalStateException, not the IllegalArgumentException that a syntax error raises. The
     * reader must skip such a line and keep streaming, or one odd event kills the whole answer.
     */
    @Test
    fun an_unexpectedly_shaped_event_is_skipped_and_the_stream_continues() =
        runTest {
            val ssePayload =
                """
                data: {"type": "delta", "content": "before"}

                data: {"type": {"unexpected": "shape"}, "content": "ignored"}

                data: {"type": "delta", "content": "after"}

                data: [DONE]

                """.trimIndent()

            val channel = ByteReadChannel(ssePayload.encodeToByteArray())
            val events = reader.readEvents(channel).toList()

            assertEquals(2, events.size, "The malformed line should be skipped, not end the stream")
            assertEquals("before", (events[0] as ChatStreamEvent.Delta).content)
            assertEquals("after", (events[1] as ChatStreamEvent.Delta).content)
        }
}
