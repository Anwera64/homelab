package com.homelab.household.presentation.chatsession

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.household.domain.exception.ApprovalPendingException
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.AgentPersonality
import com.homelab.household.domain.model.AnswerPart
import com.homelab.household.domain.model.ChatMessage
import com.homelab.household.domain.model.ChatStreamEvent
import com.homelab.household.domain.model.ConversationSession
import com.homelab.household.domain.model.MessageRole
import com.homelab.household.domain.model.MessageStatus
import com.homelab.household.domain.model.ProposalStatus
import com.homelab.household.domain.model.ToolSummary
import com.homelab.household.domain.usecase.CreateSessionUseCase
import com.homelab.household.domain.usecase.DecideToolProposalUseCase
import com.homelab.household.domain.usecase.GetAgentUseCase
import com.homelab.household.domain.usecase.GetCurrentUserUseCase
import com.homelab.household.domain.usecase.GetSessionUseCase
import com.homelab.household.domain.usecase.ListAgentsUseCase
import com.homelab.household.domain.usecase.ListHouseholdMembersUseCase
import com.homelab.household.domain.usecase.RegenerateAnswerUseCase
import com.homelab.household.domain.usecase.ResumeTurnUseCase
import com.homelab.household.domain.usecase.StreamChatTurnUseCase
import com.homelab.household.domain.usecase.ToggleSecretModeUseCase
import com.homelab.household.domain.util.runCatchingSafe
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class ChatSessionViewModel(
    private val streamChatTurnUseCase: StreamChatTurnUseCase,
    private val getSessionUseCase: GetSessionUseCase,
    private val listAgentsUseCase: ListAgentsUseCase,
    private val createSessionUseCase: CreateSessionUseCase,
    private val regenerateAnswerUseCase: RegenerateAnswerUseCase,
    private val resumeTurnUseCase: ResumeTurnUseCase,
    private val decideToolProposalUseCase: DecideToolProposalUseCase,
    private val toggleSecretModeUseCase: ToggleSecretModeUseCase,
    /** Finds the Coordinator by its slug when the whole list cannot be had. */
    private val getAgentUseCase: GetAgentUseCase,
    /** Tells "yours" apart from someone else's agent in the picker. */
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    /** Names the owner of someone else's agent in the picker. */
    private val listHouseholdMembersUseCase: ListHouseholdMembersUseCase,
    /** Times each stretch of thinking for the answer's "Thought for N s". Injected so a test can move it. */
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChatSessionUiState())
    val uiState: StateFlow<ChatSessionUiState> = _uiState.asStateFlow()

    /**
     * Numbers the placeholder ids a send carries until the hub answers with a real one.
     *
     * This used to be a clock reading, which is not an identity: two sends close enough together
     * read the same instant, and the pair then shares an id. Both the `Done` and the failure paths
     * find their message by `id`, so a collision lets one answer rewrite the status of every
     * message it collided with. A counter is unique by construction rather than by timing. Reads
     * and writes stay on the main dispatcher, the same one `viewModelScope` and every caller use.
     */
    private var nextTempMessageNumber = 0L

    /** The turn being followed, so coming back to the app can swap a slow wait for a fresh ask. */
    private var followJob: Job? = null

    /** The question the followed turn answers, so a resumed turn still marks it sent. */
    private var followingUserMessageId: String? = null

    private var opened = false

    /**
     * Opens a conversation, or prepares one that does not exist yet.
     *
     * A null [sessionId] is the hero + : the screen shows the agent's greeting and nothing is
     * created on the hub until the first message is sent, so a chat nobody spoke in is never left
     * behind. It starts with the built-in Coordinator; [selectAgent] can change that until the
     * first message, after which the session is bound to its agent for life.
     *
     * Only the first call opens anything. This ViewModel lives as long as its screen's place in
     * the back stack, so a later call is the screen coming back from one it opened (connecting a
     * calendar): starting over then would throw away a new chat mid-conversation.
     */
    fun open(sessionId: String?) {
        if (opened) return
        opened = true
        if (sessionId != null) {
            loadSession(sessionId)
        } else {
            startNewChat()
        }
    }

    /**
     * Fetches the open conversation again, once, when something outside it changed what the hub
     * saved: a calendar connected from a fix card, which the hub marks on the failed steps. A new
     * chat nobody has spoken in has nothing on the hub to fetch.
     */
    fun refresh() {
        val session = _uiState.value.session ?: return
        loadSession(session.id)
    }

    private fun startNewChat() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            // A list that cannot be had is not a chat that cannot start: the Coordinator is fetched
            // on its own, and the picker says what went missing.
            val listed = runCatchingSafe { listAgentsUseCase() }.getOrNull()
            val agent =
                if (listed != null) {
                    listed.firstOrNull { it.slug == BUILT_IN_AGENT_SLUG } ?: listed.firstOrNull()
                } else {
                    runCatchingSafe { getAgentUseCase(BUILT_IN_AGENT_SLUG) }.getOrNull()
                }
            val choices = choicesFor(listed ?: listOfNotNull(agent))
            _uiState.update {
                it.copy(
                    isLoading = false,
                    session = null,
                    messages = emptyList(),
                    agents = choices,
                    agentsFailed = listed == null,
                    selectedAgentId = agent?.id,
                    agentName = agent?.name.orEmpty(),
                    agentAvatar = agent?.avatar.orEmpty(),
                    agentTagline = agent?.description.orEmpty(),
                )
            }
        }
    }

    /**
     * Who is signed in and who else lives here only decide the owner chip, so either failing
     * leaves the cards without it rather than without the cards.
     */
    private suspend fun choicesFor(agents: List<AgentPersonality>): List<AgentChoice> {
        if (agents.isEmpty()) return emptyList()
        val myId = runCatchingSafe { getCurrentUserUseCase() }.getOrNull()?.id
        val membersById =
            runCatchingSafe { listHouseholdMembersUseCase() }
                .getOrNull()
                .orEmpty()
                .associateBy { it.id }
        return agents.map { it.toAgentChoice(myId, membersById) }
    }

    /**
     * Starts the new chat with another agent. Ignored once the chat exists: a conversation keeps
     * the agent it was started with (design notes §4).
     */
    fun selectAgent(agentId: String) {
        _uiState.update { state ->
            val choice = state.agents.firstOrNull { it.id == agentId }
            if (!state.canChangeAgent || choice == null) {
                state
            } else {
                state.copy(
                    selectedAgentId = choice.id,
                    agentName = choice.name,
                    agentAvatar = choice.avatar,
                    agentTagline = choice.tagline,
                )
            }
        }
    }

    /** Asks for the list again after it failed. Whatever is chosen stays chosen. */
    fun retryAgents() {
        viewModelScope.launch {
            val listed = runCatchingSafe { listAgentsUseCase() }.getOrNull() ?: return@launch
            val choices = choicesFor(listed)
            _uiState.update { it.copy(agents = choices, agentsFailed = false) }
        }
    }

    private suspend fun defaultAgent(): AgentPersonality? {
        val agents = listAgentsUseCase()
        return agents.firstOrNull { it.slug == BUILT_IN_AGENT_SLUG } ?: agents.firstOrNull()
    }

    fun loadSession(sessionId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val (session, messages) = getSessionUseCase(sessionId)
                // The agent is chrome, not the conversation: failing to name it must not stop the
                // transcript being read.
                val agent =
                    runCatchingSafe { listAgentsUseCase() }
                        .getOrNull()
                        ?.firstOrNull { it.id == session.agentId }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        session = session,
                        messages = messages,
                        agentName = agent?.name ?: session.agentName.orEmpty(),
                        agentAvatar = agent?.avatar ?: session.agentAvatar.orEmpty(),
                        agentTagline = agent?.description.orEmpty(),
                        isSecretLocked = session.isSecretLocked,
                        // A card left waiting is still waiting: the chat reopens on it.
                        turnState = if (session.awaitingApproval) TurnState.AwaitingApproval else it.turnState,
                    )
                }
                pickUpUnansweredQuestion(session, messages)
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to load session",
                    )
                }
            }
        }
    }

    fun composerTextChanged(text: String) {
        _uiState.update { it.copy(composerText = text) }
    }

    fun sendMessage(
        content: String,
        autoApproveWrites: Boolean = false,
    ) {
        if (!_uiState.value.canSend) return

        // A card is waiting, and the hub would refuse the message. Nothing is sent and nothing is
        // declined on the member's behalf: the text stays where it is and the screen says why.
        if (_uiState.value.turnState == TurnState.AwaitingApproval) {
            _uiState.update { it.copy(holdingForCard = true, composerText = content) }
            return
        }

        val existing = _uiState.value.session
        if (existing == null) {
            // The conversation is created by the first thing said in it, not by opening the screen.
            viewModelScope.launch {
                try {
                    // The chosen agent, or — if opening could not find even the Coordinator — one
                    // more try now that the hub may be back.
                    val agentId =
                        _uiState.value.selectedAgentId
                            ?: defaultAgent()?.id
                            ?: error("No agent to talk to")
                    val created = createSessionUseCase(agentId = agentId)
                    _uiState.update { it.copy(session = created) }
                    send(content, autoApproveWrites)
                } catch (e: Throwable) {
                    _uiState.update { it.copy(errorMessage = e.message ?: "Failed to start a chat") }
                }
            }
            return
        }

        send(content, autoApproveWrites)
    }

    private fun send(
        content: String,
        autoApproveWrites: Boolean,
    ) {
        val currentSession = _uiState.value.session ?: return
        val lastAssistantId = _uiState.value.lastAssistantMessageId
        val tempMessageId = "temp-user-${currentSession.id}-${nextTempMessageNumber++}"
        val userMsg =
            ChatMessage(
                id = tempMessageId,
                sessionId = currentSession.id,
                role = MessageRole.USER,
                content = content,
                status = MessageStatus.SENDING,
            )

        // The message is on its way, so the composer lets go of it — and not a moment earlier.
        _uiState.update {
            it.startingTurn().copy(
                messages = it.messages + userMsg,
                composerText = "",
            )
        }

        follow(
            turn =
                streamChatTurnUseCase(
                    sessionId = currentSession.id,
                    content = content,
                    autoApproveWrites = autoApproveWrites,
                    afterAssistantMessageId = lastAssistantId,
                ),
            sessionId = currentSession.id,
            userMessageId = tempMessageId,
        )
    }

    /**
     * A conversation opened with its last question still unanswered — sent, then left before the
     * answer came, or the app closed.
     *
     * Leaving the screen stopped the listening, not the hub: if it is still writing, the answer is
     * followed from its first word, as if the screen had never been left. If it is not, and no
     * answer came, the turn died, and the screen says so and offers another go instead of showing
     * a question that looks like it is waiting on nothing.
     */
    private fun pickUpUnansweredQuestion(
        session: ConversationSession,
        messages: List<ChatMessage>,
    ) {
        if (messages.lastOrNull()?.role != MessageRole.USER) return

        if (!session.turnRunning) {
            _uiState.update { it.copy(turnState = TurnState.Failed) }
            return
        }

        _uiState.update { it.startingTurn().copy(turnState = TurnState.Reconnecting) }
        follow(
            turn =
                resumeTurnUseCase(
                    sessionId = session.id,
                    afterAssistantMessageId = _uiState.value.lastAssistantMessageId,
                ),
            sessionId = session.id,
            userMessageId = null,
            resuming = true,
        )
    }

    /**
     * The app is on screen again — unlocked, or back from another app.
     *
     * A turn left waiting (reconnecting, or past the wait and still working) was most likely
     * waiting on nothing but a phone that could not reach the hub. Now it can, so it asks at once
     * for the rest of the answer instead of sitting out the rest of a backoff; the words already
     * on screen stay, and the answer carries on after them.
     */
    fun onForeground() {
        val currentSession = _uiState.value.session ?: return
        val turnState = _uiState.value.turnState
        if (turnState != TurnState.Reconnecting && turnState != TurnState.StillWorking) return

        follow(
            turn =
                resumeTurnUseCase(
                    sessionId = currentSession.id,
                    afterAssistantMessageId = _uiState.value.lastAssistantMessageId,
                ),
            sessionId = currentSession.id,
            userMessageId = followingUserMessageId,
            resuming = true,
        )
    }

    /**
     * Asks for the answer again after the model failed to produce one.
     *
     * The question is already on the hub and stays where it is; only the answer is asked for
     * again, which is exactly what the screen promises.
     */
    fun regenerate() {
        val currentSession = _uiState.value.session ?: return
        if (_uiState.value.turnState != TurnState.Failed) return

        _uiState.update { it.startingTurn() }

        follow(
            turn =
                regenerateAnswerUseCase(
                    sessionId = currentSession.id,
                    afterAssistantMessageId = _uiState.value.lastAssistantMessageId,
                ),
            sessionId = currentSession.id,
            userMessageId = null,
        )
    }

    /**
     * Follows a turn to whichever end it reaches, and puts the outcome where it belongs.
     *
     * The distinction the old version got wrong: a failure *before* the first word means the
     * question never arrived, so it is marked on the user's message and offered again. A failure
     * after it means the question is on the hub and being answered — so the partial text stays,
     * and the state goes on the turn rather than on a question that was never at fault.
     *
     * [userMessageId] is null when regenerating, because there is no new question to blame.
     *
     * [resuming] picks up a turn already under way: the question is known to have arrived, and the
     * words and parts on screen are where this one carries on from rather than starts over.
     */
    private fun follow(
        turn: Flow<ChatStreamEvent>,
        sessionId: String,
        userMessageId: String?,
        resuming: Boolean = false,
    ) {
        followJob?.cancel()
        followingUserMessageId = userMessageId
        val carriedText = if (resuming) _uiState.value.streamingMessage.orEmpty() else ""
        val carriedParts = if (resuming) _uiState.value.parts else emptyList()
        followJob =
            viewModelScope.launch {
                var accumulated = carriedText
                var delivered = resuming
                val answer = AnswerPartsBuilder(timeSource, carriedParts)

                turn
                    .catch { e ->
                        if (delivered) {
                            // The question arrived; only the wait broke. Keep what was being read.
                            _uiState.update { it.copy(turnState = TurnState.Failed) }
                            return@catch
                        }
                        if (e is ApprovalPendingException) {
                            // A card this phone had not seen yet, left by another: the message goes
                            // back in the composer, held the same way, and the chat is read again
                            // to show the card.
                            holdUnsent(userMessageId)
                            loadSession(sessionId)
                            return@catch
                        }
                        val failedStatus =
                            if (e is ServerOfflineException) {
                                MessageStatus.FAILED_OFFLINE
                            } else {
                                MessageStatus.FAILED_ERROR
                            }
                        _uiState.update { state ->
                            state.copy(
                                streamingMessage = null,
                                turnState = TurnState.Idle,
                                errorMessage = e.message ?: "Streaming failed",
                                messages =
                                    state.messages.map { msg ->
                                        if (msg.id == userMessageId) msg.copy(status = failedStatus) else msg
                                    },
                            )
                        }
                    }.collect { event ->
                        when (event) {
                            // The hub has the question. The receipt says so now rather than when the
                            // answer lands, which on a cold model can be a minute away.
                            is ChatStreamEvent.Accepted -> {
                                delivered = true
                                _uiState.update { state ->
                                    state.copy(
                                        messages =
                                            state.messages.map { msg ->
                                                if (msg.id ==
                                                    userMessageId
                                                ) {
                                                    msg.copy(status = MessageStatus.SENT)
                                                } else {
                                                    msg
                                                }
                                            },
                                    )
                                }
                            }

                            is ChatStreamEvent.Reasoning -> {
                                answer.thinking()
                                _uiState.update { it.copy(isThinking = true) }
                            }

                            is ChatStreamEvent.ToolExecuting -> {
                                answer.stopThinking()
                                _uiState.update {
                                    it.copy(
                                        activeTool = event.tool,
                                        activeToolAction = event.action,
                                        isThinking = false,
                                        parts = answer.parts(),
                                    )
                                }
                            }

                            is ChatStreamEvent.ToolResult -> {
                                answer.tool(event.tool, event.success, event.summary)
                                _uiState.update {
                                    it.copy(activeTool = null, activeToolAction = null, parts = answer.parts())
                                }
                            }

                            is ChatStreamEvent.Delta -> {
                                delivered = true
                                accumulated += event.content
                                answer.text(event.content)
                                _uiState.update {
                                    it.copy(
                                        streamingMessage = accumulated,
                                        turnState = TurnState.Streaming,
                                        isThinking = false,
                                        parts = answer.parts(),
                                    )
                                }
                            }

                            is ChatStreamEvent.ToolApprovalProposal -> {
                                answer.proposal(event)
                                _uiState.update { it.copy(isThinking = false, parts = answer.parts()) }
                            }

                            is ChatStreamEvent.ToolDeclined -> {
                                answer.declined(event.tool, event.summary)
                                _uiState.update { it.copy(parts = answer.parts()) }
                            }

                            // Paused on its cards: the answer so far is saved, and put where a
                            // finished one goes, with the cards in it.
                            is ChatStreamEvent.AwaitingApproval -> {
                                answer.stopThinking()
                                val paused =
                                    ChatMessage(
                                        id = event.messageId,
                                        sessionId = sessionId,
                                        role = MessageRole.ASSISTANT,
                                        content = event.assistantContent,
                                        status = MessageStatus.SENT,
                                        parts = event.parts.ifEmpty { answer.parts() },
                                    )
                                _uiState.update { state ->
                                    state.endingTurn(userMessageId, paused).copy(turnState = TurnState.AwaitingApproval)
                                }
                            }

                            is ChatStreamEvent.Done -> {
                                answer.stopThinking()
                                val assistantMsg =
                                    ChatMessage(
                                        id = event.messageId,
                                        sessionId = sessionId,
                                        role = MessageRole.ASSISTANT,
                                        content = event.assistantContent,
                                        status = MessageStatus.SENT,
                                        // The hub's record wins: it saw the whole turn, where this
                                        // phone may have joined it part way. An older hub sends
                                        // none, and what was watched here is the next best thing.
                                        parts = event.parts.ifEmpty { answer.parts() },
                                    )
                                _uiState.update { state -> state.endingTurn(userMessageId, assistantMsg) }
                            }

                            // The stream is gone but the hub has not finished; the words so far stay.
                            is ChatStreamEvent.Reconnecting -> {
                                _uiState.update { it.copy(turnState = TurnState.Reconnecting) }
                            }

                            is ChatStreamEvent.StillWorking -> {
                                _uiState.update { it.copy(turnState = TurnState.StillWorking) }
                            }

                            is ChatStreamEvent.TurnFailed -> {
                                answer.stopThinking()
                                _uiState.update {
                                    it.copy(
                                        turnState = TurnState.Failed,
                                        isThinking = false,
                                        activeTool = null,
                                        activeToolAction = null,
                                        parts = answer.parts(),
                                    )
                                }
                            }

                            else -> {}
                        }
                    }
            }
    }

    /**
     * The member's answer to one card.
     *
     * The paused answer leaves the transcript and becomes the answer being written again, with the
     * card marked, so what follows - the write running, the model going on - lands after it exactly
     * as a live turn would. While other cards of its step still wait the hub only records this one
     * and the turn pauses again; after the last it carries on to its end, as the same message.
     */
    fun decide(
        toolCallId: String,
        approved: Boolean,
        modifiedArguments: Map<String, Any?>? = null,
    ) {
        val state = _uiState.value
        val currentSession = state.session ?: return
        if (state.turnState != TurnState.AwaitingApproval) return
        val paused = state.messages.lastOrNull { it.role == MessageRole.ASSISTANT } ?: return
        if (state.pendingProposals.none { it.toolCallId == toolCallId }) return

        val decided = if (approved) ProposalStatus.Approved else ProposalStatus.Declined
        val parts =
            paused.parts.map { part ->
                if (part is AnswerPart.Proposal && part.toolCallId == toolCallId) part.copy(status = decided) else part
            }
        _uiState.update {
            it.startingTurn().copy(
                messages = it.messages.filterNot { msg -> msg.id == paused.id },
                streamingMessage = paused.content,
                parts = parts,
                holdingForCard = false,
            )
        }

        follow(
            turn = decideToolProposalUseCase(currentSession.id, toolCallId, approved, modifiedArguments),
            sessionId = currentSession.id,
            userMessageId = null,
            resuming = true,
        )
    }

    /** A message the hub would not take while a card waits: back in the composer, and held. */
    private fun holdUnsent(userMessageId: String?) {
        _uiState.update { state ->
            val unsent = state.messages.firstOrNull { it.id == userMessageId }
            state.copy(
                messages = state.messages.filterNot { it.id == userMessageId },
                composerText = unsent?.content ?: state.composerText,
                streamingMessage = null,
                turnState = TurnState.AwaitingApproval,
                holdingForCard = true,
                parts = emptyList(),
            )
        }
    }

    private companion object {
        /** The seeded Home & Life Coordinator, which every new chat starts with. */
        const val BUILT_IN_AGENT_SLUG = "assistant"
    }

    fun toggleSecret(isSecret: Boolean) {
        val currentSession = _uiState.value.session ?: return
        viewModelScope.launch {
            try {
                val updated = toggleSecretModeUseCase(currentSession.id, isSecret)
                _uiState.update { it.copy(session = updated) }
            } catch (e: Throwable) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to toggle secret mode") }
            }
        }
    }
}

/**
 * A turn that has ended, in the transcript: its question marked sent, and its answer in place of
 * an earlier copy of itself - an answer that paused on a card carries on as the same message.
 */
private fun ChatSessionUiState.endingTurn(
    userMessageId: String?,
    answer: ChatMessage,
) = copy(
    streamingMessage = null,
    turnState = TurnState.Idle,
    messages =
        messages
            .filterNot { it.id == answer.id }
            .map { msg -> if (msg.id == userMessageId) msg.copy(status = MessageStatus.SENT) else msg } + answer,
    isThinking = false,
    activeTool = null,
    activeToolAction = null,
    parts = emptyList(),
)

/** A new turn: nothing is carried over from the last one's thinking, tools or failure. */
private fun ChatSessionUiState.startingTurn() =
    copy(
        streamingMessage = "",
        turnState = TurnState.Streaming,
        errorMessage = null,
        isThinking = false,
        activeTool = null,
        activeToolAction = null,
        parts = emptyList(),
    )

/**
 * An answer's parts, built as its events arrive: each stretch of text, each stretch of thinking,
 * and each tool, in the order they happened.
 *
 * Thinking is timed from the first thought of a stretch to whatever ends it: a word, a tool, the
 * end of the turn. A tool's own running time is not thinking. [carried] is what a resumed turn
 * already showed, which it carries on from rather than starts over.
 */
private class AnswerPartsBuilder(
    private val timeSource: TimeSource,
    carried: List<AnswerPart>,
) {
    private val parts = carried.toMutableList()
    private var thinkingSince: TimeMark? = null

    fun parts(): List<AnswerPart> = parts.toList()

    fun thinking() {
        if (thinkingSince == null) thinkingSince = timeSource.markNow()
    }

    fun stopThinking() {
        val since = thinkingSince ?: return
        thinkingSince = null
        parts += AnswerPart.Thought(since.elapsedNow().toShownSeconds())
    }

    fun text(content: String) {
        stopThinking()
        val last = parts.lastOrNull()
        if (last is AnswerPart.Text) {
            parts[parts.lastIndex] = last.copy(content = last.content + content)
        } else {
            parts += AnswerPart.Text(content)
        }
    }

    /**
     * A write that was approved runs where its card was, the way the hub saves it; any other tool
     * is a new step at the end.
     */
    fun tool(
        name: String,
        succeeded: Boolean,
        summary: ToolSummary? = null,
    ) {
        stopThinking()
        val step = if (succeeded) AnswerPart.ToolDone(name, summary) else AnswerPart.ToolFailed(name, summary)
        val card = decidedCard(name, ProposalStatus.Approved)
        if (card >= 0) parts[card] = step else parts += step
    }

    fun proposal(event: ChatStreamEvent.ToolApprovalProposal) {
        stopThinking()
        parts += AnswerPart.Proposal(event.toolCallId, event.tool, event.action, event.arguments)
    }

    fun declined(
        name: String,
        summary: ToolSummary?,
    ) {
        val step = AnswerPart.Declined(name, summary)
        val card = decidedCard(name, ProposalStatus.Declined)
        if (card >= 0) parts[card] = step else parts += step
    }

    /** The first card of [tool] answered with [status] that has not become a step yet, or -1. */
    private fun decidedCard(
        tool: String,
        status: ProposalStatus,
    ): Int = parts.indexOfFirst { it is AnswerPart.Proposal && it.tool == tool && it.status == status }
}

/** Whole seconds, and never zero: a turn that thought at all thought for "1 s", not "0 s". */
private fun Duration.toShownSeconds(): Int = maxOf(1L, (inWholeMilliseconds / 1000.0).roundToLong()).toInt()
