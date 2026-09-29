package com.homelab.household.app.screens.conversation

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.homelab.household.domain.model.AnswerPart
import com.homelab.household.domain.model.ChatMessage
import com.homelab.household.domain.model.ConversationSession
import com.homelab.household.domain.model.EventMoment
import com.homelab.household.domain.model.MessageRole
import com.homelab.household.domain.model.MessageStatus
import com.homelab.household.domain.model.ProposalDetails
import com.homelab.household.domain.model.ToolAction
import com.homelab.household.domain.model.ToolFailureReason
import com.homelab.household.domain.model.ToolSource
import com.homelab.household.domain.model.ToolSummary
import com.homelab.household.presentation.chatsession.AgentChoice
import com.homelab.household.presentation.chatsession.AgentOwner
import com.homelab.household.presentation.chatsession.AutomaticWrite
import com.homelab.household.presentation.chatsession.ChatSessionUiState
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
        parts: List<AnswerPart> = emptyList(),
    ) = ChatMessage(id = id, sessionId = "s-1", role = role, content = content, status = status, parts = parts)

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

    private val reconnectAnswer = "Once it’s reconnected, ask again and I’ll add it straight away."

    private val refusedAdd =
        ToolSummary(action = ToolAction.Create, reason = ToolFailureReason.CalendarRejected)

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
                    parts = listOf(AnswerPart.Thought(3)),
                ),
            // Issue #33: each tool sits between the words either side of it, never above them all.
            "The answer arrives, a tool between its words" to
                agent.copy(
                    messages = listOf(question),
                    streamingMessage =
                        "Let me check your calendar.Tomorrow's fairly light. The panel review at 14:30 is the only fixed",
                    turnState = TurnState.Streaming,
                    parts =
                        listOf(
                            AnswerPart.Thought(4),
                            AnswerPart.Text("Let me check your calendar."),
                            AnswerPart.ToolDone("calendar_read"),
                            AnswerPart.Text("Tomorrow's fairly light. The panel review at 14:30 is the only fixed"),
                        ),
                ),
            // #40: each step says what it found, or why it couldn't.
            "Researching: a search, a page read and one blocked" to
                agent.copy(
                    messages = listOf(question),
                    streamingMessage = "The South China Morning Post blocks automated reading, so",
                    turnState = TurnState.Streaming,
                    parts =
                        listOf(
                            AnswerPart.ToolDone(
                                "searxng_search",
                                ToolSummary(query = "Hong Kong press freedom", count = 2, sources = listOf(rsf, scmp)),
                            ),
                            AnswerPart.ToolDone("read_page", ToolSummary(sources = listOf(rsf))),
                            AnswerPart.ToolFailed(
                                "read_page",
                                ToolSummary(
                                    reason = ToolFailureReason.Blocked,
                                    sources = listOf(scmp.copy(title = "")),
                                ),
                            ),
                            AnswerPart.Text("The South China Morning Post blocks automated reading, so"),
                        ),
                ),
            // Once it is done, each run of steps folds into one line where it happened.
            "A finished answer, its steps folded" to
                agent.copy(
                    messages =
                        listOf(
                            question,
                            said(
                                "m-2",
                                "Let me check your calendar.\n\nTomorrow's fairly light. Nothing in the evening.",
                                MessageRole.ASSISTANT,
                                parts =
                                    listOf(
                                        AnswerPart.Thought(4),
                                        AnswerPart.Text("Let me check your calendar."),
                                        AnswerPart.ToolDone("calendar_read"),
                                        AnswerPart.Thought(2),
                                        AnswerPart.Text("Tomorrow's fairly light. Nothing in the evening."),
                                    ),
                            ),
                        ),
                ),
            // #40: the fold says what its steps did, and that one of them failed.
            "A researched answer, its steps folded" to
                agent.copy(
                    messages =
                        listOf(
                            question,
                            said(
                                "m-2",
                                "The South China Morning Post blocks automated reading, so I leaned on the other two.",
                                MessageRole.ASSISTANT,
                                parts =
                                    listOf(
                                        AnswerPart.ToolDone(
                                            "searxng_search",
                                            ToolSummary(
                                                query = "Hong Kong press freedom",
                                                count = 1,
                                                sources = listOf(rsf),
                                            ),
                                        ),
                                        AnswerPart.ToolDone("read_page", ToolSummary(sources = listOf(rsf))),
                                        AnswerPart.ToolFailed(
                                            "read_page",
                                            ToolSummary(
                                                reason = ToolFailureReason.Blocked,
                                                sources = listOf(scmp.copy(title = "")),
                                            ),
                                        ),
                                        AnswerPart.ToolDone("read_page", ToolSummary(sources = listOf(hkja))),
                                        AnswerPart.ToolDone("lookup_sources"),
                                        AnswerPart.Text(
                                            "The South China Morning Post blocks automated reading, so I leaned on the other two.",
                                        ),
                                    ),
                            ),
                        ),
                ),
            // The tool is done and the next words are not here yet: still working, and it says so.
            "Between a tool and the next words" to
                agent.copy(
                    messages = listOf(question),
                    streamingMessage = "Let me check your calendar.",
                    turnState = TurnState.Streaming,
                    parts =
                        listOf(
                            AnswerPart.Text("Let me check your calendar."),
                            AnswerPart.ToolDone("calendar_read"),
                        ),
                ),
            "Thinking mid-answer" to
                agent.copy(
                    messages = listOf(question),
                    streamingMessage = "Let me check your calendar.",
                    turnState = TurnState.Streaming,
                    isThinking = true,
                    parts = listOf(AnswerPart.Text("Let me check your calendar.")),
                ),
            "A tool that couldn't run" to
                agent.copy(
                    messages = listOf(question),
                    streamingMessage = "I couldn't reach your calendar just now, so I can't say for certain.",
                    turnState = TurnState.Streaming,
                    parts =
                        listOf(
                            AnswerPart.Thought(2),
                            AnswerPart.ToolFailed("calendar_read"),
                            AnswerPart.Text("I couldn't reach your calendar just now, so I can't say for certain."),
                        ),
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
            // Canvas: ToolApproveRemove. Red, and yes is "Remove": losing something is never the easy tap.
            "Approving a removal" to
                agent.copy(
                    messages =
                        listOf(
                            said("m-1", "Cancel the print shop pickup, we'll do it Monday", MessageRole.USER),
                            said(
                                "m-2",
                                "I'll take it off your calendar.",
                                MessageRole.ASSISTANT,
                                parts =
                                    listOf(
                                        AnswerPart.Text("I'll take it off your calendar."),
                                        AnswerPart.Proposal(
                                            toolCallId = "c-1",
                                            tool = "calendar_write",
                                            action = ToolAction.Delete,
                                            details =
                                                ProposalDetails.CalendarEvent(
                                                    title = "Print shop",
                                                    start = EventMoment(2026, 9, 11, hour = 18, minute = 0),
                                                    end = null,
                                                    allDay = false,
                                                ),
                                        ),
                                    ),
                            ),
                        ),
                    turnState = TurnState.AwaitingApproval,
                ),
            // The turn carried on: one write done, the other declined, both on their record lines.
            "Approved and declined" to
                agent.copy(
                    messages =
                        listOf(
                            said(
                                "m-1",
                                "Dinner Saturday at 20:30, and remind me to buy flowers Friday",
                                MessageRole.USER,
                            ),
                            said(
                                "m-2",
                                "Dinner's in. I left the flowers out, as you asked.",
                                MessageRole.ASSISTANT,
                                parts =
                                    listOf(
                                        AnswerPart.ToolDone(
                                            "calendar_write",
                                            ToolSummary(title = "Dinner together", action = ToolAction.Create),
                                        ),
                                        AnswerPart.Declined(
                                            "calendar_write",
                                            ToolSummary(title = "Buy flowers", action = ToolAction.Create),
                                        ),
                                        AnswerPart.Text("Dinner's in. I left the flowers out, as you asked."),
                                    ),
                            ),
                        ),
                ),
            // Canvas: ToolFailed. The calendar refused the sign-in, so the answer offers the fix.
            "A tool that failed" to
                agent.copy(
                    messages =
                        listOf(
                            said("m-1", "Put the print shop cutoff in my calendar", MessageRole.USER),
                            said(
                                "m-2",
                                reconnectAnswer,
                                MessageRole.ASSISTANT,
                                parts =
                                    listOf(
                                        AnswerPart.ToolFailed("calendar_write", refusedAdd),
                                        AnswerPart.Text(reconnectAnswer),
                                    ),
                            ),
                        ),
                ),
            // The same, after other steps: the red step waits in the fold, the card stays out.
            "A tool that failed, among other steps" to
                agent.copy(
                    messages =
                        listOf(
                            said("m-1", "Put the print shop cutoff in my calendar", MessageRole.USER),
                            said(
                                "m-2",
                                reconnectAnswer,
                                MessageRole.ASSISTANT,
                                parts =
                                    listOf(
                                        AnswerPart.Thought(2),
                                        AnswerPart.ToolFailed("calendar_write", refusedAdd),
                                        AnswerPart.Text(reconnectAnswer),
                                    ),
                            ),
                        ),
                ),
            // Canvas: ToolFixedCard. A calendar connected since the step failed, and this is still
            // the latest answer, so its question can be asked again from the card.
            "A fixed tool, the latest answer" to
                agent.copy(
                    messages =
                        listOf(
                            said("m-1", "Put the print shop cutoff in my calendar", MessageRole.USER),
                            said(
                                "m-2",
                                reconnectAnswer,
                                MessageRole.ASSISTANT,
                                parts =
                                    listOf(
                                        AnswerPart.ToolFailed("calendar_write", refusedAdd.copy(fixed = true)),
                                        AnswerPart.Text(reconnectAnswer),
                                    ),
                            ),
                        ),
                ),
            // Canvas: ToolFixedLater. The same, once a newer message has been sent: no Ask again.
            "A fixed tool, a newer message after it" to
                agent.copy(
                    messages =
                        listOf(
                            said("m-1", "Put the print shop cutoff in my calendar", MessageRole.USER),
                            said(
                                "m-2",
                                reconnectAnswer,
                                MessageRole.ASSISTANT,
                                parts =
                                    listOf(
                                        AnswerPart.ToolFailed("calendar_write", refusedAdd.copy(fixed = true)),
                                        AnswerPart.Text(reconnectAnswer),
                                    ),
                            ),
                            said("m-3", "What else is on Friday?", MessageRole.USER),
                            said("m-4", "Just the dentist at 10:00.", MessageRole.ASSISTANT),
                        ),
                ),
            // Send was pressed while a card waits: the words stay, and the line says why.
            "Waiting on a card, Send held" to
                agent.copy(
                    messages =
                        listOf(
                            said("m-1", "Put dinner in the calendar for Saturday", MessageRole.USER),
                            said(
                                "m-2",
                                "",
                                MessageRole.ASSISTANT,
                                parts =
                                    listOf(
                                        AnswerPart.Proposal(
                                            toolCallId = "c-1",
                                            tool = "calendar_write",
                                            action = ToolAction.Create,
                                            details =
                                                ProposalDetails.CalendarEvent(
                                                    title = "Dinner together",
                                                    start = EventMoment(2026, 10, 3, hour = 20, minute = 30),
                                                    end = null,
                                                    allDay = false,
                                                ),
                                        ),
                                    ),
                            ),
                        ),
                    turnState = TurnState.AwaitingApproval,
                    composerText = "Also lunch on Sunday?",
                    holdingForCard = true,
                ),
            // Canvas: ToolAutoApprove. An add can be made automatic from its card.
            "Auto-approving an add" to
                agent.copy(
                    messages =
                        listOf(
                            said("m-1", "Can you put dinner in the calendar for Saturday?", MessageRole.USER),
                            said(
                                "m-2",
                                "Saturday at 20:00 works for both of you. I can add it now.",
                                MessageRole.ASSISTANT,
                                parts =
                                    listOf(
                                        AnswerPart.ToolDone("calendar_read"),
                                        AnswerPart.Text("Saturday at 20:00 works for both of you. I can add it now."),
                                        AnswerPart.Proposal(
                                            toolCallId = "c-1",
                                            tool = "calendar_write",
                                            action = ToolAction.Create,
                                            details =
                                                ProposalDetails.CalendarEvent(
                                                    title = "Dinner together",
                                                    start = EventMoment(2026, 10, 3, hour = 20, minute = 0),
                                                    end = null,
                                                    allDay = false,
                                                ),
                                        ),
                                    ),
                            ),
                        ),
                    turnState = TurnState.AwaitingApproval,
                ),
            // Canvas: ToolAutoApproved. The next add ran without a card, and its record says so.
            "Adding is automatic" to
                agent.copy(
                    messages =
                        listOf(
                            said("m-1", "Add the print shop cutoff for Thursday too", MessageRole.USER),
                            said(
                                "m-2",
                                "Added, Thursday at 18:00.",
                                MessageRole.ASSISTANT,
                                parts =
                                    listOf(
                                        AnswerPart.ToolDone(
                                            "calendar_write",
                                            ToolSummary(title = "Print shop cutoff", action = ToolAction.Create),
                                            automatic = true,
                                        ),
                                        AnswerPart.Text("Added, Thursday at 18:00."),
                                    ),
                            ),
                        ),
                    madeAutomatic = AutomaticWrite("calendar_write", ToolAction.Create),
                ),
        )

    override val values: Sequence<ChatSessionUiState> = named.map { it.second }.asSequence()

    override fun getDisplayName(index: Int): String = named[index].first
}

private val rsf = ToolSource("Hong Kong: press freedom index", "https://rsf.org/en/country/hong-kong")
private val scmp = ToolSource("Hong Kong news", "https://www.scmp.com/news/hong-kong")
private val hkja = ToolSource("Annual report: a shrinking space", "https://www.hkja.org.hk/")
