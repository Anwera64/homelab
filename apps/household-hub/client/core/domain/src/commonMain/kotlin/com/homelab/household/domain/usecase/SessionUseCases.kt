package com.homelab.household.domain.usecase

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.model.ChatMessage
import com.homelab.household.domain.model.ConversationSession
import com.homelab.household.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow

class CreateSessionUseCase(private val sessionRepository: SessionRepository) {
    suspend operator fun invoke(agentId: String, title: String = "New Conversation", isSecret: Boolean = false): ConversationSession {
        if (agentId.isBlank()) {
            throw ValidationException("Agent ID cannot be blank")
        }
        return sessionRepository.createSession(agentId.trim(), title.trim(), isSecret)
    }
}

class GetSessionUseCase(private val sessionRepository: SessionRepository) {
    suspend operator fun invoke(sessionId: String): Pair<ConversationSession, List<ChatMessage>> {
        if (sessionId.isBlank()) throw ValidationException("Session ID cannot be blank")
        return sessionRepository.getSession(sessionId)
    }
}

class ListSessionsUseCase(private val sessionRepository: SessionRepository) {
    suspend operator fun invoke(): List<ConversationSession> = sessionRepository.listSessions()
}

class ArchiveSessionUseCase(private val sessionRepository: SessionRepository) {
    suspend operator fun invoke(sessionId: String) {
        if (sessionId.isBlank()) throw ValidationException("Session ID cannot be blank")
        sessionRepository.archiveSession(sessionId)
    }
}

class ToggleSecretModeUseCase(private val sessionRepository: SessionRepository) {
    suspend operator fun invoke(sessionId: String, isSecret: Boolean): ConversationSession {
        if (sessionId.isBlank()) throw ValidationException("Session ID cannot be blank")
        return sessionRepository.toggleSecretMode(sessionId, isSecret)
    }
}

class DeleteSessionUseCase(private val sessionRepository: SessionRepository) {
    suspend operator fun invoke(sessionId: String) {
        if (sessionId.isBlank()) throw ValidationException("Session ID cannot be blank")
        sessionRepository.deleteSession(sessionId)
    }
}

class ApproveToolProposalUseCase(private val sessionRepository: SessionRepository) {
    suspend operator fun invoke(
        sessionId: String,
        toolCallId: String,
        approved: Boolean,
        modifiedArguments: Map<String, Any?>? = null
    ): Boolean {
        if (sessionId.isBlank()) throw ValidationException("Session ID cannot be blank")
        if (toolCallId.isBlank()) throw ValidationException("Tool call ID cannot be blank")
        return sessionRepository.approveToolProposal(sessionId, toolCallId, approved, modifiedArguments)
    }
}

class ObserveMessagesUseCase(private val sessionRepository: SessionRepository) {
    operator fun invoke(sessionId: String): Flow<List<ChatMessage>> = sessionRepository.observeMessages(sessionId)
}

class RetryMessageUseCase(private val sessionRepository: SessionRepository) {
    suspend operator fun invoke(messageId: String) = sessionRepository.retryMessage(messageId)
}
