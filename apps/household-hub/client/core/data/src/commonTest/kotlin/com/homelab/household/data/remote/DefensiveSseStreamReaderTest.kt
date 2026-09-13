package com.homelab.household.data.remote

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
    fun parse_sse_stream_decodes_deltas_and_done() = runTest {
        val ssePayload = """
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
    fun parse_sse_stream_strips_control_delimiter_tokens() = runTest {
        val ssePayload = """
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
}
