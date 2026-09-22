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

    /**
     * The hub's two ways of saying a turn went wrong, which the reader used to drop on the floor.
     *
     * `turn_failed` means the question is on the hub and only the answer is missing; `error` means
     * the turn never got that far. Skipping them left the stream ending with nothing terminal in
     * it at all, and a screen that waits forever for a word that is never coming.
     */
    @Test
    fun the_two_ways_a_turn_fails_are_both_read() =
        runTest {
            val ssePayload =
                """
                data: {"type": "turn_failed", "error": "model timed out"}

                data: [DONE]

                """.trimIndent()

            val channel = ByteReadChannel(ssePayload.encodeToByteArray())
            val events = reader.readEvents(channel).toList()

            assertEquals(listOf(ChatStreamEvent.TurnFailed), events)
        }

    @Test
    fun a_turn_that_never_opened_is_read_as_an_error_carrying_its_reason() =
        runTest {
            val ssePayload =
                """
                data: {"type": "error", "error": "Agent personality not found"}

                data: [DONE]

                """.trimIndent()

            val channel = ByteReadChannel(ssePayload.encodeToByteArray())
            val events = reader.readEvents(channel).toList()

            assertEquals(1, events.size)
            assertEquals("Agent personality not found", (events[0] as ChatStreamEvent.StreamError).message)
        }

    /** The hub has saved the question. The earliest thing it can honestly say. */
    @Test
    fun the_hub_saying_it_has_the_question_is_read() =
        runTest {
            val ssePayload =
                """
                data: {"type": "accepted"}

                data: [DONE]

                """.trimIndent()

            val events = reader.readEvents(ByteReadChannel(ssePayload.encodeToByteArray())).toList()

            assertEquals(listOf(ChatStreamEvent.Accepted), events)
        }

    @Test
    fun reasoning_is_read_as_it_arrives() =
        runTest {
            val ssePayload =
                """
                data: {"type": "reasoning", "content": "Okay, the user wants"}

                data: {"type": "reasoning", "content": " tomorrow."}

                data: [DONE]

                """.trimIndent()

            val events = reader.readEvents(ByteReadChannel(ssePayload.encodeToByteArray())).toList()

            assertEquals(
                listOf(ChatStreamEvent.Reasoning("Okay, the user wants"), ChatStreamEvent.Reasoning(" tomorrow.")),
                events,
            )
        }

    /**
     * The hub has always sent a tool's outcome inside `data`, and the reader looked for it at the
     * top level — so every tool arrived nameless and every failure arrived as a success.
     */
    @Test
    fun a_tool_s_outcome_is_read_from_where_the_hub_puts_it() =
        runTest {
            val ssePayload =
                """
                data: {"type": "tool_result", "data": {"tool": "calendar_read", "success": false, "error": "Unauthorized"}}

                data: [DONE]

                """.trimIndent()

            val events = reader.readEvents(ByteReadChannel(ssePayload.encodeToByteArray())).toList()

            val result = events.single() as ChatStreamEvent.ToolResult
            assertEquals("calendar_read", result.tool)
            assertEquals(false, result.success)
            assertEquals("Unauthorized", result.error)
        }
}
