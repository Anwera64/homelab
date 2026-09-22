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
    val updatedAt: String? = null,
    /**
     * What a Chats row draws beside the title, resolved by the hub.
     *
     * [agentName] and [agentAvatar] are nullable because a conversation can outlive its agent:
     * purging one clears the link. Slice 7 decides what that row looks like; until then a row
     * without them draws plainly rather than crashing.
     */
    val lastMessagePreview: String? = null,
    val agentName: String? = null,
    val agentAvatar: String? = null,
    /** Whether the hub is generating an answer for this conversation right now. */
    val turnRunning: Boolean = false,
)
