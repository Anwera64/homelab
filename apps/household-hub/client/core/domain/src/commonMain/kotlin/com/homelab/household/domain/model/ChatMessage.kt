package com.homelab.household.domain.model

data class ChatMessage(
    val id: String,
    val sessionId: String,
    val role: MessageRole,
    val content: String,
    val status: MessageStatus = MessageStatus.SENT,
    val metadata: Map<String, Any?> = emptyMap(),
    val createdAt: String? = null,
)
