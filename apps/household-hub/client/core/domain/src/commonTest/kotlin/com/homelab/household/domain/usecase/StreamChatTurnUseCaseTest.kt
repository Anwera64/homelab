package com.homelab.household.domain.usecase

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.model.ChatStreamEvent
import com.homelab.household.domain.repository.SessionRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StreamChatTurnUseCaseTest {

    private val sessionRepo = mockk<SessionRepository>()
    private val streamChatTurnUseCase = StreamChatTurnUseCase(sessionRepo)

    @Test
    fun execute_with_blank_content_throws_validation_error() {
        assertThrows(ValidationException::class.java) {
            streamChatTurnUseCase("session-1", "   ", autoApproveWrites = false)
        }
    }

    @Test
    fun execute_streams_events_from_repository() = runTest {
        val events = listOf(
            ChatStreamEvent.Delta(content = "Hello"),
            ChatStreamEvent.Delta(content = " world"),
            ChatStreamEvent.Done(
                messageId = "asst-msg-1",
                assistantContent = "Hello world",
                suggestSecretMode = false,
                isTurnSecret = false,
                agentName = "Assistant"
            )
        )
        every {
            sessionRepo.streamChatTurn("session-1", "Hi", autoApproveWrites = false)
        } returns flowOf(*events.toTypedArray())

        val result = streamChatTurnUseCase("session-1", "Hi", autoApproveWrites = false).toList()

        assertEquals(3, result.size)
        assertTrue(result[0] is ChatStreamEvent.Delta)
        assertEquals("Hello", (result[0] as ChatStreamEvent.Delta).content)
        assertEquals(" world", (result[1] as ChatStreamEvent.Delta).content)
        assertTrue(result[2] is ChatStreamEvent.Done)
        assertEquals("Hello world", (result[2] as ChatStreamEvent.Done).assistantContent)
    }
}
