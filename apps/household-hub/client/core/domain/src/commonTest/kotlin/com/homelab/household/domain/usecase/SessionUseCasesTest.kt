package com.homelab.household.domain.usecase

import com.homelab.household.domain.assertThrowsSuspend
import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.model.ChatMessage
import com.homelab.household.domain.model.ConversationSession
import com.homelab.household.domain.model.MessageRole
import com.homelab.household.domain.model.MessageStatus
import com.homelab.household.domain.repository.SessionRepository
import com.homelab.household.domain.usecase.impl.ArchiveSessionUseCaseImpl
import com.homelab.household.domain.usecase.impl.CreateSessionUseCaseImpl
import com.homelab.household.domain.usecase.impl.GetSessionUseCaseImpl
import com.homelab.household.domain.usecase.impl.ToggleSecretModeUseCaseImpl
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SessionUseCasesTest {

    private val sessionRepo = mockk<SessionRepository>()
    private val createSessionUseCase = CreateSessionUseCaseImpl(sessionRepo)
    private val getSessionUseCase = GetSessionUseCaseImpl(sessionRepo)
    private val archiveSessionUseCase = ArchiveSessionUseCaseImpl(sessionRepo)
    private val toggleSecretModeUseCase = ToggleSecretModeUseCaseImpl(sessionRepo)

    private val dummySession = ConversationSession(
        id = "session-1",
        userId = "user-1",
        agentId = "agent-1",
        title = "Research Session",
        isSecret = false,
        isArchived = false,
        createdAt = "2026-09-08T12:00:00Z",
        updatedAt = "2026-09-08T12:00:00Z"
    )

    @Test
    fun create_session_with_valid_agent_returns_session() = runTest {
        coEvery { sessionRepo.createSession("agent-1", "Research Session", false) } returns dummySession

        val session = createSessionUseCase("agent-1", "Research Session", false)

        assertEquals("session-1", session.id)
        assertEquals("agent-1", session.agentId)
        coVerify(exactly = 1) { sessionRepo.createSession("agent-1", "Research Session", false) }
    }

    @Test
    fun create_session_with_blank_agent_id_throws_validation_error() = runTest {
        assertThrowsSuspend<ValidationException> {
            createSessionUseCase("", "Research Session", false)
        }
    }

    @Test
    fun get_session_returns_session_and_messages() = runTest {
        val messages = listOf(
            ChatMessage(
                id = "msg-1",
                sessionId = "session-1",
                role = MessageRole.USER,
                content = "Hello",
                status = MessageStatus.SENT,
                createdAt = "2026-09-08T12:01:00Z"
            )
        )
        coEvery { sessionRepo.getSession("session-1") } returns Pair(dummySession, messages)

        val (session, fetchedMessages) = getSessionUseCase("session-1")

        assertEquals("session-1", session.id)
        assertEquals(1, fetchedMessages.size)
        assertEquals("Hello", fetchedMessages.first().content)
    }

    @Test
    fun archive_session_delegates_to_repo() = runTest {
        coEvery { sessionRepo.archiveSession("session-1") } returns Unit

        archiveSessionUseCase("session-1")

        coVerify(exactly = 1) { sessionRepo.archiveSession("session-1") }
    }

    @Test
    fun toggle_secret_mode_delegates_to_repo() = runTest {
        coEvery { sessionRepo.toggleSecretMode("session-1", true) } returns dummySession.copy(isSecret = true)

        val result = toggleSecretModeUseCase("session-1", true)

        assertEquals(true, result.isSecret)
    }
}
