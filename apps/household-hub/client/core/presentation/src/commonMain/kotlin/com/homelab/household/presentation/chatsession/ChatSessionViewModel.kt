package com.homelab.household.presentation.chatsession

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.ChatMessage
import com.homelab.household.domain.model.ChatStreamEvent
import com.homelab.household.domain.model.MessageRole
import com.homelab.household.domain.model.MessageStatus
import com.homelab.household.domain.usecase.ApproveToolProposalUseCase
import com.homelab.household.domain.usecase.GetSessionUseCase
import com.homelab.household.domain.usecase.StreamChatTurnUseCase
import com.homelab.household.domain.usecase.ToggleSecretModeUseCase
import com.homelab.household.presentation.chatsession.ChatSessionUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatSessionViewModel(
    private val streamChatTurnUseCase: StreamChatTurnUseCase,
    private val getSessionUseCase: GetSessionUseCase,
    private val approveToolProposalUseCase: ApproveToolProposalUseCase,
    private val toggleSecretModeUseCase: ToggleSecretModeUseCase
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

    fun loadSession(sessionId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val (session, messages) = getSessionUseCase(sessionId)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        session = session,
                        messages = messages,
                        isSecretLocked = session.isSecretLocked
                    )
                }
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to load session"
                    )
                }
            }
        }
    }

    fun sendMessage(content: String, autoApproveWrites: Boolean = false) {
        val currentSession = _uiState.value.session ?: return
        val tempMessageId = "temp-user-${currentSession.id}-${nextTempMessageNumber++}"
        val userMsg = ChatMessage(
            id = tempMessageId,
            sessionId = currentSession.id,
            role = MessageRole.USER,
            content = content,
            status = MessageStatus.SENDING
        )

        _uiState.update {
            it.copy(
                messages = it.messages + userMsg,
                streamingMessage = "",
                errorMessage = null
            )
        }

        viewModelScope.launch {
            var accumulated = ""
            streamChatTurnUseCase(currentSession.id, content, autoApproveWrites)
                .catch { e ->
                    val failedStatus = if (e is ServerOfflineException) {
                        MessageStatus.FAILED_OFFLINE
                    } else {
                        MessageStatus.FAILED_ERROR
                    }
                    _uiState.update { state ->
                        state.copy(
                            streamingMessage = null,
                            errorMessage = e.message ?: "Streaming failed",
                            messages = state.messages.map { msg ->
                                if (msg.id == tempMessageId) msg.copy(status = failedStatus) else msg
                            }
                        )
                    }
                }
                .collect { event ->
                    when (event) {
                        is ChatStreamEvent.Delta -> {
                            accumulated += event.content
                            _uiState.update { it.copy(streamingMessage = accumulated) }
                        }
                        is ChatStreamEvent.ToolApprovalProposal -> {
                            _uiState.update { it.copy(pendingToolProposal = event) }
                        }
                        is ChatStreamEvent.Done -> {
                            val assistantMsg = ChatMessage(
                                id = event.messageId,
                                sessionId = currentSession.id,
                                role = MessageRole.ASSISTANT,
                                content = event.assistantContent,
                                status = MessageStatus.SENT
                            )
                            _uiState.update { state ->
                                state.copy(
                                    streamingMessage = null,
                                    messages = state.messages.map { msg ->
                                        if (msg.id == tempMessageId) msg.copy(status = MessageStatus.SENT) else msg
                                    } + assistantMsg
                                )
                            }
                        }
                        else -> {}
                    }
                }
        }
    }

    fun approveTool(toolCallId: String, approved: Boolean, modifiedArguments: Map<String, Any?>? = null) {
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
