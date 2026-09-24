package com.homelab.household.presentation.chatsession

import com.homelab.household.domain.model.AnswerPart
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * When the screen owes the reader a sign that the turn is still going.
 *
 * Words arriving say it themselves, and so does a running tool's chip. Every other moment of a
 * live turn is a wait, and a wait with nothing on screen reads as an answer that stopped.
 */
class ChatSessionUiStateTest {
    private val streaming = ChatSessionUiState(turnState = TurnState.Streaming, streamingMessage = "")

    @Test
    fun `GIVEN nothing back yet WHEN streaming THEN it is waiting for words`() {
        assertTrue(streaming.isWaitingForWords)
    }

    @Test
    fun `GIVEN words then a finished tool WHEN nothing new has come THEN it is waiting for words`() {
        val state =
            streaming.copy(
                streamingMessage = "Let me check.",
                parts = listOf(AnswerPart.Text("Let me check."), AnswerPart.ToolDone("web_search")),
            )

        assertTrue(state.isWaitingForWords)
    }

    @Test
    fun `GIVEN the model thinking after its first words WHEN streaming THEN it is waiting for words`() {
        val state =
            streaming.copy(
                streamingMessage = "Let me check.",
                parts = listOf(AnswerPart.Text("Let me check.")),
                isThinking = true,
            )

        assertTrue(state.isWaitingForWords)
    }

    @Test
    fun `GIVEN words arriving WHEN streaming THEN it is not waiting`() {
        val state =
            streaming.copy(
                streamingMessage = "Let me check. It stays",
                parts =
                    listOf(
                        AnswerPart.Text("Let me check."),
                        AnswerPart.ToolDone("web_search"),
                        AnswerPart.Text("It stays"),
                    ),
            )

        assertFalse(state.isWaitingForWords)
    }

    @Test
    fun `GIVEN words with no parts recorded WHEN streaming THEN it is not waiting`() {
        assertFalse(streaming.copy(streamingMessage = "Three things, in order").isWaitingForWords)
    }

    @Test
    fun `GIVEN a tool running WHEN streaming THEN it is not waiting because the chip says so`() {
        val state =
            streaming.copy(
                streamingMessage = "Let me check.",
                parts = listOf(AnswerPart.Text("Let me check.")),
                activeTool = "web_search",
            )

        assertFalse(state.isWaitingForWords)
    }

    @Test
    fun `GIVEN a turn that is not streaming WHEN drawn THEN it is not waiting for words`() {
        val lastStep = listOf(AnswerPart.Text("Let me check."), AnswerPart.ToolDone("web_search"))
        listOf(TurnState.Idle, TurnState.Failed, TurnState.Reconnecting, TurnState.StillWorking).forEach { turn ->
            assertFalse(streaming.copy(turnState = turn, parts = lastStep).isWaitingForWords, "$turn")
        }
    }
}
