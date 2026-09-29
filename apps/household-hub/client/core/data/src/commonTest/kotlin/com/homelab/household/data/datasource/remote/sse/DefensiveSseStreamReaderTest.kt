package com.homelab.household.data.datasource.remote.sse

import com.homelab.household.domain.model.AnswerPart
import com.homelab.household.domain.model.ChatStreamEvent
import com.homelab.household.domain.model.EventMoment
import com.homelab.household.domain.model.ProposalDetails
import com.homelab.household.domain.model.ProposalStatus
import com.homelab.household.domain.model.ToolAction
import com.homelab.household.domain.model.ToolSource
import com.homelab.household.domain.model.ToolSummary
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
    fun `GIVEN a done event with parts WHEN read THEN the parts arrive in order`() =
        runTest {
            val ssePayload =
                """
                data: {"type": "done", "message_id": "m1", "assistant_content": "A.\n\nB", "parts": [{"type": "text", "content": "A."}, {"type": "tool", "tool": "web_search", "success": true}, {"type": "thought", "seconds": 3}, {"type": "text", "content": "B"}]}

                """.trimIndent()

            val done =
                reader
                    .readEvents(
                        ByteReadChannel(ssePayload.encodeToByteArray()),
                    ).toList()
                    .single() as ChatStreamEvent.Done

            assertEquals(
                listOf(
                    AnswerPart.Text("A."),
                    AnswerPart.ToolDone("web_search"),
                    AnswerPart.Thought(3),
                    AnswerPart.Text("B"),
                ),
                done.parts,
            )
        }

    @Test
    fun `GIVEN a done event from a hub that keeps no parts WHEN read THEN it has none`() =
        runTest {
            val ssePayload =
                """
                data: {"type": "done", "message_id": "m1", "assistant_content": "Hello"}

                """.trimIndent()

            val done =
                reader
                    .readEvents(
                        ByteReadChannel(ssePayload.encodeToByteArray()),
                    ).toList()
                    .single() as ChatStreamEvent.Done

            assertEquals(emptyList(), done.parts)
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
            assertTrue(delta.content.contains("Filtered content###"))
        }

    /** A table's separator row is Markdown, not a model delimiter; without its dashes it is no table. */
    @Test
    fun `GIVEN a delta with a table separator WHEN it is read THEN the dashes survive`() =
        runTest {
            // GIVEN
            val ssePayload =
                """
                data: {"type": "delta", "content": "|---|---|"}

                data: [DONE]

                """.trimIndent()

            // WHEN
            val events = reader.readEvents(ByteReadChannel(ssePayload.encodeToByteArray())).toList()

            // THEN
            assertEquals("|---|---|", (events.single() as ChatStreamEvent.Delta).content)
        }

    @Test
    fun `GIVEN a delta with a heading and a divider WHEN it is read THEN both survive`() =
        runTest {
            // GIVEN
            val ssePayload =
                """
                data: {"type": "delta", "content": "### Plan\n\n---"}

                data: [DONE]

                """.trimIndent()

            // WHEN
            val events = reader.readEvents(ByteReadChannel(ssePayload.encodeToByteArray())).toList()

            // THEN
            assertEquals("### Plan\n\n---", (events.single() as ChatStreamEvent.Delta).content)
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

    /** A tool's summary is what the phone draws on its line: a search's query and results (#40). */
    @Test
    fun `GIVEN a tool result with a summary WHEN read THEN the event carries it`() =
        runTest {
            val ssePayload =
                """
                data: {"type": "tool_result", "data": {"tool": "searxng_search", "success": true, "summary": {"query": "dinner", "count": 1, "sources": [{"title": "Menu", "url": "https://lapubilla.cat/"}]}}}

                data: [DONE]

                """.trimIndent()

            val events = reader.readEvents(ByteReadChannel(ssePayload.encodeToByteArray())).toList()

            val result = events.single() as ChatStreamEvent.ToolResult
            assertEquals(
                ToolSummary(
                    query = "dinner",
                    count = 1,
                    sources = listOf(ToolSource("Menu", "https://lapubilla.cat/")),
                ),
                result.summary,
            )
        }

    /** A write the member made automatic runs without a card, and its record says so. */
    @Test
    fun `GIVEN an automatic write's result WHEN read THEN the event says it was automatic`() =
        runTest {
            val ssePayload =
                """
                data: {"type": "tool_result", "data": {"tool": "calendar_write", "success": true, "auto": true}}

                data: {"type": "tool_result", "data": {"tool": "calendar_read", "success": true}}

                data: [DONE]

                """.trimIndent()

            val events = reader.readEvents(ByteReadChannel(ssePayload.encodeToByteArray())).toList()

            assertEquals(listOf(true, false), events.map { (it as ChatStreamEvent.ToolResult).automatic })
        }

    /** A running write says which action it is, so a removal is never shown as "Adding…". */
    @Test
    fun `GIVEN a running write WHEN read THEN the event carries its action`() =
        runTest {
            val ssePayload =
                """
                data: {"type": "tool_executing", "tool": "calendar_write", "arguments": {"action": "delete", "event_id": "e1"}}

                data: [DONE]

                """.trimIndent()

            val events = reader.readEvents(ByteReadChannel(ssePayload.encodeToByteArray())).toList()

            assertEquals(
                ChatStreamEvent.ToolExecuting(tool = "calendar_write", action = ToolAction.Delete),
                events.single(),
            )
        }

    @Test
    fun `GIVEN a running read WHEN read THEN it has no action`() =
        runTest {
            val ssePayload =
                """
                data: {"type": "tool_executing", "tool": "searxng_search", "arguments": {"query": "dinner"}}

                data: [DONE]

                """.trimIndent()

            val events = reader.readEvents(ByteReadChannel(ssePayload.encodeToByteArray())).toList()

            assertEquals(ChatStreamEvent.ToolExecuting(tool = "searxng_search"), events.single())
        }

    /** `id:` lines let a data source remember where to resume a dropped stream from. */
    @Test
    fun `GIVEN frames with ids WHEN they are read THEN each id is reported after its event`() =
        runTest {
            // GIVEN
            val ssePayload =
                """
                id: t:1
                data: {"type": "delta", "content": "Hello"}

                id: t:2
                data: {"type": "delta", "content": " world"}

                data: [DONE]

                """.trimIndent()
            val reportedIds = mutableListOf<String>()

            // WHEN
            val events =
                reader
                    .readEvents(
                        ByteReadChannel(ssePayload.encodeToByteArray()),
                        onEventId = { reportedIds.add(it) },
                    ).toList()

            // THEN
            assertEquals(listOf("t:1", "t:2"), reportedIds)
            assertEquals("Hello", (events[0] as ChatStreamEvent.Delta).content)
            assertEquals(" world", (events[1] as ChatStreamEvent.Delta).content)
        }

    @Test
    fun `GIVEN a frame without an id after one with an id WHEN read THEN no id is reported for it`() =
        runTest {
            // GIVEN
            val ssePayload =
                """
                id: t:1
                data: {"type": "delta", "content": "Hello"}

                data: {"type": "delta", "content": " world"}

                data: [DONE]

                """.trimIndent()
            val reportedIds = mutableListOf<String>()

            // WHEN
            val events =
                reader
                    .readEvents(
                        ByteReadChannel(ssePayload.encodeToByteArray()),
                        onEventId = { reportedIds.add(it) },
                    ).toList()

            // THEN
            assertEquals(listOf("t:1"), reportedIds)
            assertEquals(2, events.size)
        }

    // ---- approval cards (slice 4, PR 3) -----------------------------------

    @Test
    fun `GIVEN a write that needs asking WHEN its proposal is read THEN the card has its call id and action and details`() =
        runTest {
            val ssePayload =
                """
                data: {"type": "tool_approval_proposal", "tool_call_id": "c1", "tool": "calendar_write", "action": "delete", "arguments": {"action": "delete", "title": "Print shop cutoff", "start_time": "2026-09-11T18:00:00", "is_all_day": false, "reminder": 15}}

                """.trimIndent()

            val proposal =
                reader.readEvents(ByteReadChannel(ssePayload.encodeToByteArray())).toList().single()
                    as ChatStreamEvent.ToolApprovalProposal

            assertEquals("c1", proposal.toolCallId)
            assertEquals("calendar_write", proposal.tool)
            assertEquals(ToolAction.Delete, proposal.action)
            assertEquals(
                ProposalDetails.CalendarEvent(
                    title = "Print shop cutoff",
                    start = EventMoment(2026, 9, 11, hour = 18, minute = 0),
                    end = null,
                    allDay = false,
                ),
                proposal.details,
            )
        }

    @Test
    fun `GIVEN a turn paused on a card WHEN its end is read THEN the saved answer carries the waiting card`() =
        runTest {
            val ssePayload =
                """
                data: {"type": "awaiting_approval", "message_id": "m1", "assistant_content": "I can add it now.", "agent_name": "Home Coordinator", "parts": [{"type": "text", "content": "I can add it now."}, {"type": "proposal", "tool_call_id": "c1", "tool": "calendar_write", "action": "create", "arguments": {"title": "Dinner together"}, "status": "pending"}]}

                """.trimIndent()

            val paused =
                reader.readEvents(ByteReadChannel(ssePayload.encodeToByteArray())).toList().single()
                    as ChatStreamEvent.AwaitingApproval

            assertEquals("m1", paused.messageId)
            assertEquals(
                listOf(
                    AnswerPart.Text("I can add it now."),
                    AnswerPart.Proposal(
                        toolCallId = "c1",
                        tool = "calendar_write",
                        action = ToolAction.Create,
                        details = ProposalDetails.CalendarEvent("Dinner together", null, null, allDay = false),
                        status = ProposalStatus.Pending,
                    ),
                ),
                paused.parts,
            )
        }

    @Test
    fun `GIVEN a declined write WHEN the answer carries on THEN the step and the saved part both name it`() =
        runTest {
            val ssePayload =
                """
                data: {"type": "tool_declined", "tool": "calendar_write", "summary": {"action": "create", "title": "Buy flowers"}}

                data: {"type": "done", "message_id": "m1", "assistant_content": "Left it off.", "parts": [{"type": "declined", "tool": "calendar_write", "summary": {"action": "create", "title": "Buy flowers"}}, {"type": "text", "content": "Left it off."}]}

                """.trimIndent()

            val events = reader.readEvents(ByteReadChannel(ssePayload.encodeToByteArray())).toList()

            val named = ToolSummary(action = ToolAction.Create, title = "Buy flowers")
            assertEquals(ChatStreamEvent.ToolDeclined("calendar_write", named), events[0])
            assertEquals(
                AnswerPart.Declined("calendar_write", named),
                (events[1] as ChatStreamEvent.Done).parts.first(),
            )
        }
}
