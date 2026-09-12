package com.homelab.household.presentation.viewmodel.chatsession.model

import com.homelab.household.domain.model.ChatMessage
import com.homelab.household.domain.model.ChatStreamEvent
import com.homelab.household.domain.model.ConversationSession

data class ChatSessionUiState(
    val isLoading: Boolean = false,
    val session: ConversationSession? = null,
    val messages: List<ChatMessage> = emptyList(),
    val streamingMessage: String? = null,
    val pendingToolProposal: ChatStreamEvent.ToolApprovalProposal? = null,
    val errorMessage: String? = null,
    val isSecretLocked: Boolean = false
)
