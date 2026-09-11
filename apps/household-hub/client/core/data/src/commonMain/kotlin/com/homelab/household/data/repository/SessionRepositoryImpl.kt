package com.homelab.household.data.repository

import com.homelab.household.data.dto.ChatMessageReadDto
import com.homelab.household.data.dto.ChatTurnRequestDto
import com.homelab.household.data.dto.SessionCreateDto
import com.homelab.household.data.dto.SessionDetailReadDto
import com.homelab.household.data.dto.SessionReadDto
import com.homelab.household.data.dto.SessionSecretToggleDto
import com.homelab.household.data.dto.ToolApprovalRequestDto
import com.homelab.household.data.dto.ToolApprovalResponseDto
import com.homelab.household.data.mapper.ChatMessageDataMapper
import com.homelab.household.data.mapper.SessionDataMapper
import com.homelab.household.data.remote.DefensiveSseStreamReader
import com.homelab.household.data.remote.NetworkExceptionHelper
import com.homelab.household.domain.exception.DomainException
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.ChatMessage
import com.homelab.household.domain.model.ChatStreamEvent
import com.homelab.household.domain.model.ConversationSession
import com.homelab.household.domain.repository.SessionRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

class SessionRepositoryImpl(
    private val client: HttpClient,
    private val baseUrl: String,
    private val pollDelayMs: Long = 1000L,
    private val sseStreamReader: DefensiveSseStreamReader = DefensiveSseStreamReader()
) : SessionRepository {

    private val lockedSecretSessions = mutableSetOf<String>()
    private val sessionMessagesCache = mutableMapOf<String, MutableStateFlow<List<ChatMessage>>>()

    override suspend fun listSessions(): List<ConversationSession> {
        return try {
            val dtoList = client.get("$baseUrl/api/v1/sessions").body<List<SessionReadDto>>()
            dtoList.map { dto ->
                val session = SessionDataMapper.toDomain(dto)
                if (session.isSecret && lockedSecretSessions.contains(session.id)) {
                    session.copy(isSecretLocked = true)
                } else {
                    session
                }
            }
        } catch (e: Exception) {
            NetworkExceptionHelper.rethrowAsDomain(e)
        }
    }

    override suspend fun getSession(sessionId: String): Pair<ConversationSession, List<ChatMessage>> {
        return try {
            val detailDto = client.get("$baseUrl/api/v1/sessions/$sessionId").body<SessionDetailReadDto>()
            val session = SessionDataMapper.toDomain(detailDto)
            val mappedSession = if (session.isSecret && lockedSecretSessions.contains(session.id)) {
                session.copy(isSecretLocked = true)
            } else {
                session
            }
            val messages = detailDto.messages.map { ChatMessageDataMapper.toDomain(it) }
            getOrCreateMessageFlow(sessionId).value = messages
            Pair(mappedSession, messages)
        } catch (e: Exception) {
            NetworkExceptionHelper.rethrowAsDomain(e)
        }
    }

    override suspend fun createSession(
        agentId: String,
        title: String,
        isSecret: Boolean
    ): ConversationSession {
        return try {
            val response = client.post("$baseUrl/api/v1/sessions") {
                contentType(ContentType.Application.Json)
                setBody(SessionCreateDto(agent_id = agentId, title = title, is_secret = isSecret))
            }.body<SessionReadDto>()
            SessionDataMapper.toDomain(response)
        } catch (e: Exception) {
            NetworkExceptionHelper.rethrowAsDomain(e)
        }
    }

    override suspend fun archiveSession(sessionId: String) {
        try {
            client.post("$baseUrl/api/v1/sessions/$sessionId/archive")
        } catch (e: Exception) {
            NetworkExceptionHelper.rethrowAsDomain(e)
        }
    }

    override suspend fun toggleSecretMode(sessionId: String, isSecret: Boolean): ConversationSession {
        return try {
            val response = client.patch("$baseUrl/api/v1/sessions/$sessionId/secret") {
                contentType(ContentType.Application.Json)
                setBody(SessionSecretToggleDto(is_secret = isSecret))
            }.body<SessionReadDto>()
            SessionDataMapper.toDomain(response)
        } catch (e: Exception) {
            NetworkExceptionHelper.rethrowAsDomain(e)
        }
    }

    override suspend fun deleteSession(sessionId: String) {
        try {
            client.delete("$baseUrl/api/v1/sessions/$sessionId")
        } catch (e: Exception) {
            NetworkExceptionHelper.rethrowAsDomain(e)
        }
    }

    override fun streamChatTurn(
        sessionId: String,
        content: String,
        autoApproveWrites: Boolean
    ): Flow<ChatStreamEvent> = flow {
        try {
            val statement = client.preparePost("$baseUrl/api/v1/sessions/$sessionId/chat/stream") {
                contentType(ContentType.Application.Json)
                setBody(ChatTurnRequestDto(content = content, auto_approve_writes = autoApproveWrites))
            }

            statement.execute { response ->
                when (response.status) {
                    HttpStatusCode.OK -> {
                        val channel = response.bodyAsChannel()
                        sseStreamReader.readEvents(channel).collect { event ->
                            emit(event)
                        }
                    }
                    HttpStatusCode.Conflict -> {
                        // 409 Conflict: Background worker is busy or session locked -> self-healing polling
                        pollUntilFinished(sessionId).collect { event ->
                            emit(event)
                        }
                    }
                    else -> {
                        throw IllegalStateException("Unexpected status ${response.status}")
                    }
                }
            }
        } catch (e: Throwable) {
            NetworkExceptionHelper.rethrowAsDomain(e)
        }
    }

    private fun pollUntilFinished(sessionId: String): Flow<ChatStreamEvent> = flow {
        var delayMs = 500L
        var elapsedMs = 0L
        val maxWaitMs = 60_000L
        var completed = false

        while (!completed && elapsedMs < maxWaitMs) {
            delay(delayMs)
            elapsedMs += delayMs
            delayMs = (delayMs * 1.5).toLong()

            try {
                val sessionDetail = client.get("$baseUrl/api/v1/sessions/$sessionId")
                    .body<SessionDetailReadDto>()
                val messages = sessionDetail.messages
                val lastAssistant = messages.lastOrNull { it.role.equals("assistant", ignoreCase = true) }
                if (lastAssistant != null) {
                    emit(
                        ChatStreamEvent.Done(
                            messageId = lastAssistant.id,
                            assistantContent = lastAssistant.content,
                            agentName = "Assistant"
                        )
                    )
                    completed = true
                }
            } catch (e: Throwable) {
                if (NetworkExceptionHelper.isNetworkOfflineException(e)) {
                    throw ServerOfflineException(message = "Server connection lost while polling", cause = e)
                }
            }
        }

        if (!completed) {
            throw DomainException("Session inference recovery timed out after 60s")
        }
    }

    override suspend fun approveToolProposal(
        sessionId: String,
        toolCallId: String,
        approved: Boolean,
        modifiedArguments: Map<String, Any?>?
    ): Boolean {
        return try {
            val response = client.post("$baseUrl/api/v1/sessions/$sessionId/tools/approve") {
                contentType(ContentType.Application.Json)
                setBody(
                    ToolApprovalRequestDto(
                        tool_call_id = toolCallId,
                        approved = approved
                    )
                )
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            NetworkExceptionHelper.rethrowAsDomain(e)
        }
    }

    override suspend fun lockAllSecretSessions(): Int {
        val allSessions = listSessions()
        val secretSessions = allSessions.filter { it.isSecret }
        secretSessions.forEach { lockedSecretSessions.add(it.id) }
        return secretSessions.size
    }

    override suspend fun unlockSecretSession(sessionId: String, pinOrPassword: String): Boolean {
        if (pinOrPassword.isNotBlank()) {
            lockedSecretSessions.remove(sessionId)
            return true
        }
        return false
    }

    override fun observeMessages(sessionId: String): Flow<List<ChatMessage>> {
        return getOrCreateMessageFlow(sessionId)
    }

    override suspend fun retryMessage(messageId: String): Flow<ChatStreamEvent> {
        val entry = sessionMessagesCache.entries.firstOrNull { (_, flow) ->
            flow.value.any { it.id == messageId }
        } ?: throw DomainException("Message with id $messageId not found to retry")

        val sessionId = entry.key
        val message = entry.value.value.first { it.id == messageId }
        return streamChatTurn(sessionId = sessionId, content = message.content)
    }

    private fun getOrCreateMessageFlow(sessionId: String): MutableStateFlow<List<ChatMessage>> {
        return sessionMessagesCache.getOrPut(sessionId) { MutableStateFlow(emptyList()) }
    }
}
