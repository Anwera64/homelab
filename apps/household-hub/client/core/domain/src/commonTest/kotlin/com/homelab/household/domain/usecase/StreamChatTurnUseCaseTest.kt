package com.homelab.household.domain.usecase

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.model.ChatStreamEvent
import com.homelab.household.domain.repository.SessionRepository
import com.homelab.household.domain.usecase.impl.StreamChatTurnUseCaseImpl
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

class StreamChatTurnUseCaseTest {

    private val sessionRepo = mock<SessionRepository>()
    private val streamChatTurnUseCase = StreamChatTurnUseCaseImpl(sessionRepo)

    @Test
    fun execute_with_blank_content_throws_validation_error() {
        assertFailsWith<ValidationException> {
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

