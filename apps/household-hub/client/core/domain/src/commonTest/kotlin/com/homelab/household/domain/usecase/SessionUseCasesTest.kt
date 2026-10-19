package com.homelab.household.domain.usecase

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
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SessionUseCasesTest {
    private val sessionRepo = mock<SessionRepository>()
    private val createSessionUseCase = CreateSessionUseCaseImpl(sessionRepo)
    private val getSessionUseCase = GetSessionUseCaseImpl(sessionRepo)
    private val archiveSessionUseCase = ArchiveSessionUseCaseImpl(sessionRepo)
    private val toggleSecretModeUseCase = ToggleSecretModeUseCaseImpl(sessionRepo)

    private val dummySession =
        ConversationSession(
            id = "session-1",
            userId = "user-1",
            agentId = "agent-1",
            title = "Research Session",
            isSecret = false,
            isArchived = false,
            createdAt = "2026-09-08T12:00:00Z",
            updatedAt = "2026-09-08T12:00:00Z",
        )

    @Test
    fun `GIVEN a valid agent WHEN a session is created THEN the new session comes back and the repository is asked once`() =
        runTest {
            everySuspend { sessionRepo.createSession("agent-1", "Research Session", false) } returns dummySession

            val session = createSessionUseCase("agent-1", "Research Session", false)

            assertEquals("session-1", session.id)
            assertEquals("agent-1", session.agentId)
            verifySuspend(VerifyMode.exactly(1)) { sessionRepo.createSession("agent-1", "Research Session", false) }
        }

    @Test
    fun `GIVEN a blank agent id WHEN a session is created THEN it is refused as invalid`() =
        runTest {
            assertFailsWith<ValidationException> {
                createSessionUseCase("", "Research Session", false)
            }
        }

    @Test
    fun `GIVEN a session with one message WHEN it is fetched THEN the session and its message come back`() =
        runTest {
            val messages =
                listOf(
                    ChatMessage(
                        id = "msg-1",
                        sessionId = "session-1",
                        role = MessageRole.USER,
                        content = "Hello",
                        status = MessageStatus.SENT,
                        createdAt = "2026-09-08T12:01:00Z",
                    ),
                )
            everySuspend { sessionRepo.getSession("session-1") } returns Pair(dummySession, messages)

            val (session, fetchedMessages) = getSessionUseCase("session-1")

            assertEquals("session-1", session.id)
            assertEquals(1, fetchedMessages.size)
            assertEquals("Hello", fetchedMessages.first().content)
        }

    @Test
    fun `GIVEN an existing session WHEN it is archived THEN the repository archives it once`() =
        runTest {
            everySuspend { sessionRepo.archiveSession("session-1") } returns Unit

            archiveSessionUseCase("session-1")

            verifySuspend(VerifyMode.exactly(1)) { sessionRepo.archiveSession("session-1") }
        }

    @Test
    fun `GIVEN a session that is not secret WHEN secret mode is switched on THEN the session comes back secret`() =
        runTest {
            everySuspend { sessionRepo.toggleSecretMode("session-1", true) } returns dummySession.copy(isSecret = true)

            val result = toggleSecretModeUseCase("session-1", true)

            assertEquals(true, result.isSecret)
        }
}
