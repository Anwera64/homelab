package com.homelab.household.data.datasource.remote.`interface`

import com.homelab.household.data.dto.SessionDetailReadDto
import com.homelab.household.data.dto.SessionReadDto
import com.homelab.household.domain.exception.SessionConflictException
import com.homelab.household.domain.model.ChatStreamEvent
import kotlinx.coroutines.flow.Flow

/**
 * Everything the hub is asked about conversations, including the streamed turn.
 *
 * [openChatStream] is the second stated exception to "a data source returns DTOs": it emits
 * [ChatStreamEvent], a domain type, because that sealed hierarchy *is* the SSE protocol — its
 * variants are the `type` field of each event, one for one. A parallel DTO hierarchy plus a mapper
 * would restate the same five shapes for no reader's benefit. `DataLayerBoundaryTest` exempts this
 * file and its implementation by name.
 */
interface SessionRemoteDataSource {

    suspend fun listSessions(): List<SessionReadDto>

    suspend fun fetchSession(sessionId: String): SessionDetailReadDto

    suspend fun createSession(agentId: String, title: String, isSecret: Boolean): SessionReadDto

    suspend fun archiveSession(sessionId: String)

    suspend fun toggleSecretMode(sessionId: String, isSecret: Boolean): SessionReadDto

    suspend fun deleteSession(sessionId: String)

    suspend fun approveToolProposal(sessionId: String, toolCallId: String, approved: Boolean): Boolean

    /**
     * The agent's reply, token by token, for as long as the collector keeps up.
     *
     * Throws [SessionConflictException] when the hub is already working on a turn for this
     * conversation. That is a refusal rather than a failure, and what to do about it — wait, and
     * read the conversation back until the reply lands — is the repository's decision, not this
     * layer's.
     */
    fun openChatStream(sessionId: String, content: String, autoApproveWrites: Boolean): Flow<ChatStreamEvent>
}
