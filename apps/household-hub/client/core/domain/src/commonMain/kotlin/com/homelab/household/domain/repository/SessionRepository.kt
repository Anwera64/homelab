package com.homelab.household.domain.repository

import com.homelab.household.domain.model.ChatMessage
import com.homelab.household.domain.model.ChatStreamEvent
import com.homelab.household.domain.model.ConversationSession
import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    suspend fun listSessions(): List<ConversationSession>
    suspend fun getSession(sessionId: String): Pair<ConversationSession, List<ChatMessage>>
    suspend fun createSession(agentId: String, title: String = "New Conversation", isSecret: Boolean = false): ConversationSession
    suspend fun archiveSession(sessionId: String)
    suspend fun toggleSecretMode(sessionId: String, isSecret: Boolean): ConversationSession
    suspend fun deleteSession(sessionId: String)
    fun streamChatTurn(sessionId: String, content: String, autoApproveWrites: Boolean = false): Flow<ChatStreamEvent>
    suspend fun approveToolProposal(sessionId: String, toolCallId: String, approved: Boolean, modifiedArguments: Map<String, Any?>? = null): Boolean
    suspend fun lockAllSecretSessions(): Int
    /**
     * Opens a locked secret conversation, answering whether this caller is the one that opened
     * it. Takes no PIN: verifying one is [com.homelab.household.domain.usecase.UnlockSecretSessionUseCase]'s
     * job, and the data layer only records which conversations are open.
     */
    suspend fun unlockSecretSession(sessionId: String): Boolean
    fun observeMessages(sessionId: String): Flow<List<ChatMessage>>
    suspend fun retryMessage(messageId: String): Flow<ChatStreamEvent>
}
