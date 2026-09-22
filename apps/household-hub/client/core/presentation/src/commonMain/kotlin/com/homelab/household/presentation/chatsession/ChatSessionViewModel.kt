package com.homelab.household.presentation.chatsession

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.AgentPersonality
import com.homelab.household.domain.model.ChatMessage
import com.homelab.household.domain.model.ChatStreamEvent
import com.homelab.household.domain.model.MessageRole
import com.homelab.household.domain.model.MessageStatus
import com.homelab.household.domain.usecase.ApproveToolProposalUseCase
import com.homelab.household.domain.usecase.CreateSessionUseCase
import com.homelab.household.domain.usecase.GetSessionUseCase
import com.homelab.household.domain.usecase.ListAgentsUseCase
import com.homelab.household.domain.usecase.RegenerateAnswerUseCase
import com.homelab.household.domain.usecase.StreamChatTurnUseCase
import com.homelab.household.domain.usecase.ToggleSecretModeUseCase
import com.homelab.household.domain.util.runCatchingSafe
import com.homelab.household.presentation.chatsession.ChatSessionUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatSessionViewModel(
    private val streamChatTurnUseCase: StreamChatTurnUseCase,
    private val getSessionUseCase: GetSessionUseCase,
    private val listAgentsUseCase: ListAgentsUseCase,
    private val createSessionUseCase: CreateSessionUseCase,
    private val regenerateAnswerUseCase: RegenerateAnswerUseCase,
    private val approveToolProposalUseCase: ApproveToolProposalUseCase,
    private val toggleSecretModeUseCase: ToggleSecretModeUseCase,
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

    /**
     * Opens a conversation, or prepares one that does not exist yet.
     *
     * A null [sessionId] is the hero + : the screen shows the agent's greeting and nothing is
     * created on the hub until the first message is sent, so a chat nobody spoke in is never left
     * behind. Slice 3 always answers with the built-in coordinator; choosing an agent arrives with
     * the Agents screen, and a session is bound to one agent for its life either way.
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
            try {
                val agent = defaultAgent()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        session = null,
                        messages = emptyList(),
                        agentName = agent?.name.orEmpty(),
                        agentAvatar = agent?.avatar.orEmpty(),
                        agentTagline = agent?.description.orEmpty(),
                    )
                }
            } catch (e: Throwable) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Failed to start a chat") }
            }
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
                    val agent = defaultAgent() ?: error("No agent to talk to")
                    val created = createSessionUseCase(agentId = agent.id)
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

        _uiState.update {
            it.copy(
                messages = it.messages + userMsg,
                streamingMessage = "",
                turnState = TurnState.Streaming,
                errorMessage = null,
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
     * Asks for the answer again after the model failed to produce one.
     *
     * The question is already on the hub and stays where it is; only the answer is asked for
     * again, which is exactly what the screen promises.
     */
    fun regenerate() {
        val currentSession = _uiState.value.session ?: return
        if (_uiState.value.turnState != TurnState.Failed) return

        _uiState.update { it.copy(streamingMessage = "", turnState = TurnState.Streaming, errorMessage = null) }

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
     */
    private fun follow(
        turn: Flow<ChatStreamEvent>,
        sessionId: String,
        userMessageId: String?,
    ) {
        viewModelScope.launch {
            var accumulated = ""
            var delivered = false

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
                        is ChatStreamEvent.Delta -> {
                            delivered = true
                            accumulated += event.content
                            _uiState.update { it.copy(streamingMessage = accumulated, turnState = TurnState.Streaming) }
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
                            _uiState.update { state ->
                                state.copy(
                                    streamingMessage = null,
                                    turnState = TurnState.Idle,
                                    messages =
                                        state.messages.map { msg ->
                                            if (msg.id == userMessageId) msg.copy(status = MessageStatus.SENT) else msg
                                        } + assistantMsg,
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
                            _uiState.update { it.copy(turnState = TurnState.Failed) }
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
        /** The seeded Home & Life Coordinator. Slice 7 lets you pick a different one. */
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
