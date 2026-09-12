package com.homelab.household.domain.model

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
