package com.homelab.household.presentation.chats

import com.homelab.household.domain.model.ConversationSession

data class ChatsUiState(
    val isLoading: Boolean = false,
    val query: String = "",
    val visible: List<ConversationSession> = emptyList(),
    val errorMessage: String? = null,
    private val loaded: Boolean = false,
    private val total: Int = 0,
) {
    /**
     * A household that has never chatted, which is a screen of its own — no search, no secret bar,
     * one button. Both of those arrive with the first chat.
     *
     * Deliberately false until a load has actually succeeded: an unreachable hub returns no rows
     * either, and offering "Start a chat" to someone whose hub is down would be answering the
     * wrong question.
     */
    val isEmpty: Boolean
        get() = loaded && total == 0

    /** A query that found nothing, which reads differently: the chats exist, this one doesn't. */
    val hasNoMatches: Boolean
        get() = loaded && total > 0 && query.isNotBlank() && visible.isEmpty()
}
