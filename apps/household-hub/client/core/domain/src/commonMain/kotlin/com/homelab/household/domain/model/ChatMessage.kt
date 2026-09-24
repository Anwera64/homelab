package com.homelab.household.domain.model

data class ChatMessage(
    val id: String,
    val sessionId: String,
    val role: MessageRole,
    val content: String,
    val status: MessageStatus = MessageStatus.SENT,
    val metadata: Map<String, Any?> = emptyMap(),
    val createdAt: String? = null,
    /** What the answer did, in order. Empty for a question, and for an answer saved before parts were kept. */
    val parts: List<AnswerPart> = emptyList(),
)
