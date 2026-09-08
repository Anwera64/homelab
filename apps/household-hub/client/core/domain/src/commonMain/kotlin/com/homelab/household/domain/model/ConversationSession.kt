package com.homelab.household.domain.model

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL
}

enum class MessageStatus {
    SENDING,
    SENT,
    FAILED_OFFLINE,
    FAILED_ERROR
}

data class ChatMessage(
    val id: String,
    val sessionId: String,
    val role: MessageRole,
    val content: String,
    val status: MessageStatus = MessageStatus.SENT,
    val metadata: Map<String, Any?> = emptyMap(),
    val createdAt: String? = null
)

data class ConversationSession(
    val id: String,
    val userId: String,
    val agentId: String? = null,
    val title: String = "New Conversation",
    val isSecret: Boolean = false,
    val isArchived: Boolean = false,
    val isSecretLocked: Boolean = false,
    val createdAt: String? = null,
    val updatedAt: String? = null
)
