package com.homelab.household.app.screens.conversation

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.homelab.household.domain.model.ChatMessage
import com.homelab.household.domain.model.ConversationSession
import com.homelab.household.domain.model.MessageRole
import com.homelab.household.domain.model.MessageStatus
import com.homelab.household.presentation.chatsession.AgentChoice
import com.homelab.household.presentation.chatsession.AgentOwner
import com.homelab.household.presentation.chatsession.ChatSessionUiState
import com.homelab.household.presentation.chatsession.TurnRecord
import com.homelab.household.presentation.chatsession.TurnState

/**
 * A conversation in each state the canvas draws, including the four ways a turn ends.
 * `ConversationScreenTest` renders them all.
 */
class ConversationUiStateProvider : PreviewParameterProvider<ChatSessionUiState> {
    private val session = ConversationSession(id = "s-1", userId = "u-1", title = "What's left before Friday?")

    private fun said(
        id: String,
        content: String,
        role: MessageRole,
        status: MessageStatus = MessageStatus.SENT,
    ) = ChatMessage(id = id, sessionId = "s-1", role = role, content = content, status = status)

    private val agent =
        ChatSessionUiState(
            session = session,
            agentName = "Home Coordinator",
            agentAvatar = "🏡",
            agentTagline = "Schedules, meals, keeping the week straight.",
        )

    private val coordinator =
        AgentChoice(
            id = "agent-coord",
            name = "Home Coordinator",
            avatar = "🏡",
            tagline = "Schedules, meals, keeping the week straight.",
            owner = AgentOwner.BuiltIn,
            tools = listOf("calendar_read", "searxng_search"),
        )

    private val choices =
        listOf(
            coordinator,
            AgentChoice(
                id = "agent-research",
                name = "Academic Researcher",
                avatar = "📚",
                tagline = "Papers, references, reading long PDFs properly.",
                owner = AgentOwner.BuiltIn,
                tools = listOf("searxng_search", "pdf_reader"),
            ),
            AgentChoice(
                id = "agent-scout",
                name = "Hardware Scout",
                avatar = "🔧",
                tagline = "Parts, prices, whether that GPU is worth it.",
                owner = AgentOwner.Member("Liam"),
                tools = listOf("searxng_search"),
            ),
        )

    private val question = said("m-1", "What's left before Friday?", MessageRole.USER)

    private val partialAnswer =
        "Three things, in order of how much they'll bite. The panel review is tomorrow at 14:30 — " +
            "that's the one to protect. The print shop closes at 18:00 the same day, so if the boards aren't"

    /** Enough of an answer to outgrow any phone, for the states that have to scroll. */
    private val longAnswer =
        (1..40).joinToString(" ") { "Sentence $it of an answer that keeps going well past the fold." }

    private val named =
        listOf(
            // New Chat: the same screen with the greeting where the transcript will be.
            "New chat" to agent.copy(session = null, agents = choices, selectedAgentId = coordinator.id),
            // The hub answered but not with the list: the Coordinator still greets, and the picker
            // says what is missing.
            "New chat, agents didn't load" to
                agent.copy(
                    session = null,
                    agents = listOf(coordinator),
                    selectedAgentId = coordinator.id,
                    agentsFailed = true,
                ),
            "A finished exchange" to
                agent.copy(
                    messages =
                        listOf(
                            question,
                            said("m-2", "The panel review is tomorrow at 14:30.", MessageRole.ASSISTANT),
                        ),
                ),
            // Sent, and nothing back yet: the model is being loaded or is thinking. The longest
            // silence in the app, and the one that used to draw an empty bubble.
            "Thinking, before the first word" to
                agent.copy(
                    messages = listOf(question),
                    streamingMessage = "",
                    turnState = TurnState.Streaming,
                ),
            // The rest of a turn you can watch, frame by frame as the Chat Turn States canvas draws it.
            "Thinking out loud" to
                agent.copy(
                    messages = listOf(question),
                    streamingMessage = "",
                    turnState = TurnState.Streaming,
                    isThinking = true,
                ),
            "Using a tool" to
                agent.copy(
                    messages = listOf(question),
                    streamingMessage = "",
                    turnState = TurnState.Streaming,
                    activeTool = "calendar_read",
                    trail = listOf(TurnRecord.Thought(3)),
                ),
            "The answer arrives, with its trail" to
                agent.copy(
                    messages = listOf(question),
                    streamingMessage = "Tomorrow's fairly light. The panel review at 14:30 is the only fixed",
                    turnState = TurnState.Streaming,
                    trail = listOf(TurnRecord.Thought(4), TurnRecord.ToolDone("calendar_read")),
                ),
            "A finished answer and its trail" to
                agent.copy(
                    messages =
                        listOf(
                            question,
                            said("m-2", "Tomorrow's fairly light. Nothing in the evening.", MessageRole.ASSISTANT),
                        ),
                    trails = mapOf("m-2" to listOf(TurnRecord.Thought(4), TurnRecord.ToolDone("calendar_read"))),
                ),
            "A tool that couldn't run" to
                agent.copy(
                    messages = listOf(question),
                    streamingMessage = "I couldn't reach your calendar just now, so I can't say for certain.",
                    turnState = TurnState.Streaming,
                    trail = listOf(TurnRecord.Thought(2), TurnRecord.ToolFailed("calendar_read")),
                ),
            "Answering" to
                agent.copy(
                    messages = listOf(question),
                    streamingMessage = "Three things, in order of how much ",
                    turnState = TurnState.Streaming,
                ),
            "Stream dropped, reconnecting" to
                agent.copy(
                    messages = listOf(question),
                    streamingMessage = partialAnswer,
                    turnState = TurnState.Reconnecting,
                ),
            "Still working after a minute" to
                agent.copy(
                    messages = listOf(question),
                    streamingMessage = partialAnswer,
                    turnState = TurnState.StillWorking,
                ),
            // Opening a conversation that has been going a while: it starts at the newest, not
            // at whatever was said first.
            "A conversation opened again" to
                agent.copy(
                    messages =
                        (1..20).flatMap { turn ->
                            listOf(
                                said("u-$turn", "Question $turn of a long-running conversation", MessageRole.USER),
                                said(
                                    "a-$turn",
                                    "Answer $turn, at some length to fill the screen.",
                                    MessageRole.ASSISTANT,
                                ),
                            )
                        },
                ),
            "A long answer arriving" to
                agent.copy(
                    messages = listOf(question),
                    streamingMessage = longAnswer,
                    turnState = TurnState.Streaming,
                ),
            "The answer failed" to
                agent.copy(
                    messages = listOf(said("m-1", "Plan meals for the week around Friday", MessageRole.USER)),
                    streamingMessage = "",
                    turnState = TurnState.Failed,
                ),
            // The only one of the four that re-sends: the question itself never landed.
            "Never reached the hub" to
                agent.copy(
                    messages =
                        listOf(
                            said("m-1", "Can you put dinner in the calendar for Saturday?", MessageRole.USER),
                            said("m-2", "Done — Saturday at 20:00, in your iCloud calendar.", MessageRole.ASSISTANT),
                            said("m-3", "Actually, make it 20:30", MessageRole.USER, MessageStatus.FAILED_OFFLINE),
                        ),
                ),
        )

    override val values: Sequence<ChatSessionUiState> = named.map { it.second }.asSequence()

    override fun getDisplayName(index: Int): String = named[index].first
}
