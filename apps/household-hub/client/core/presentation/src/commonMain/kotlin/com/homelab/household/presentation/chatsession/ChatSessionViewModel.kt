package com.homelab.household.presentation.chatsession

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.AgentPersonality
import com.homelab.household.domain.model.ChatMessage
import com.homelab.household.domain.model.ChatStreamEvent
import com.homelab.household.domain.model.ConversationSession
import com.homelab.household.domain.model.MessageRole
import com.homelab.household.domain.model.MessageStatus
import com.homelab.household.domain.usecase.ApproveToolProposalUseCase
import com.homelab.household.domain.usecase.CreateSessionUseCase
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
    private val approveToolProposalUseCase: ApproveToolProposalUseCase,
    private val toggleSecretModeUseCase: ToggleSecretModeUseCase,
    /** Finds the Coordinator by its slug when the whole list cannot be had. */
    private val getAgentUseCase: GetAgentUseCase,
    /** Tells "yours" apart from someone else's agent in the picker. */
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    /** Names the owner of someone else's agent in the picker. */
    private val listHouseholdMembersUseCase: ListHouseholdMembersUseCase,
    /** Times the thinking for the trail's "Thought for N s". Injected so a test can move it. */
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

    /**
     * Opens a conversation, or prepares one that does not exist yet.
     *
     * A null [sessionId] is the hero + : the screen shows the agent's greeting and nothing is
     * created on the hub until the first message is sent, so a chat nobody spoke in is never left
     * behind. It starts with the built-in Coordinator; [selectAgent] can change that until the
     * first message, after which the session is bound to its agent for life.
     */
    fun open(sessionId: String?) {
        if (sessionId != null) {
            loadSession(sessionId)
        } else {
            startNewChat()
        }
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
     * words and trail on screen are where this one carries on from rather than starts over.
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
        val carriedTrail = if (resuming) _uiState.value.trail else emptyList()
        followJob =
            viewModelScope.launch {
                var accumulated = carriedText
                var delivered = resuming

                // Thinking is timed from the first thought of each stretch to whatever ends it — a
                // tool, the first word, the end of the turn — and summed across the turn.
                var thinkingSince: TimeMark? = null
                var thought = Duration.ZERO
                val tools = mutableListOf<TurnRecord>()

                fun stopThinking() {
                    thinkingSince?.let { thought += it.elapsedNow() }
                    thinkingSince = null
                }

                fun trail(): List<TurnRecord> =
                    buildList {
                        addAll(carriedTrail)
                        if (thought > Duration.ZERO) add(TurnRecord.Thought(thought.toShownSeconds()))
                        addAll(tools)
                    }

                turn
                    .catch { e ->
                        if (delivered) {
                            // The question arrived; only the wait broke. Keep what was being read.
                            _uiState.update { it.copy(turnState = TurnState.Failed) }
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
                                if (thinkingSince == null) thinkingSince = timeSource.markNow()
                                _uiState.update { it.copy(isThinking = true) }
                            }

                            is ChatStreamEvent.ToolExecuting -> {
                                stopThinking()
                                _uiState.update {
                                    it.copy(
                                        activeTool = event.tool,
                                        isThinking = false,
                                        trail = trail(),
                                    )
                                }
                            }

                            is ChatStreamEvent.ToolResult -> {
                                tools +=
                                    when {
                                        event.success -> TurnRecord.ToolDone(event.tool)
                                        else -> TurnRecord.ToolFailed(event.tool)
                                    }
                                _uiState.update { it.copy(activeTool = null, trail = trail()) }
                            }

                            is ChatStreamEvent.Delta -> {
                                delivered = true
                                if (accumulated.isEmpty()) stopThinking()
                                accumulated += event.content
                                _uiState.update {
                                    it.copy(
                                        streamingMessage = accumulated,
                                        turnState = TurnState.Streaming,
                                        isThinking = false,
                                        trail = trail(),
                                    )
                                }
                            }

                            is ChatStreamEvent.ToolApprovalProposal -> {
                                _uiState.update { it.copy(pendingToolProposal = event) }
                            }

                            is ChatStreamEvent.Done -> {
                                val assistantMsg =
                                    ChatMessage(
                                        id = event.messageId,
                                        sessionId = sessionId,
                                        role = MessageRole.ASSISTANT,
                                        content = event.assistantContent,
                                        status = MessageStatus.SENT,
                                    )
                                stopThinking()
                                val finished = trail()
                                _uiState.update { state ->
                                    state.copy(
                                        streamingMessage = null,
                                        turnState = TurnState.Idle,
                                        messages =
                                            state.messages.map { msg ->
                                                if (msg.id ==
                                                    userMessageId
                                                ) {
                                                    msg.copy(status = MessageStatus.SENT)
                                                } else {
                                                    msg
                                                }
                                            } + assistantMsg,
                                        isThinking = false,
                                        activeTool = null,
                                        trail = emptyList(),
                                        trails =
                                            if (finished.isEmpty()) {
                                                state.trails
                                            } else {
                                                state.trails +
                                                    (event.messageId to finished)
                                            },
                                    )
                                }
                            }

                            // The stream is gone but the hub has not finished; the words so far stay.
                            is ChatStreamEvent.Reconnecting -> {
                                _uiState.update { it.copy(turnState = TurnState.Reconnecting) }
                            }

                            is ChatStreamEvent.StillWorking -> {
                                _uiState.update { it.copy(turnState = TurnState.StillWorking) }
                            }

                            is ChatStreamEvent.TurnFailed -> {
                                stopThinking()
                                _uiState.update {
                                    it.copy(
                                        turnState = TurnState.Failed,
                                        isThinking = false,
                                        activeTool = null,
                                        trail = trail(),
                                    )
                                }
                            }

                            else -> {}
                        }
                    }
            }
    }

    fun approveTool(
        toolCallId: String,
        approved: Boolean,
        modifiedArguments: Map<String, Any?>? = null,
    ) {
        val currentSession = _uiState.value.session ?: return
        viewModelScope.launch {
            try {
                approveToolProposalUseCase(currentSession.id, toolCallId, approved, modifiedArguments)
                _uiState.update { it.copy(pendingToolProposal = null) }
            } catch (e: Throwable) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to approve tool") }
            }
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

/** A new turn: nothing is carried over from the last one's thinking, tools or failure. */
private fun ChatSessionUiState.startingTurn() =
    copy(
        streamingMessage = "",
        turnState = TurnState.Streaming,
        errorMessage = null,
        isThinking = false,
        activeTool = null,
        trail = emptyList(),
    )

/** Whole seconds, and never zero: a turn that thought at all thought for "1 s", not "0 s". */
private fun Duration.toShownSeconds(): Int = maxOf(1L, (inWholeMilliseconds / 1000.0).roundToLong()).toInt()
