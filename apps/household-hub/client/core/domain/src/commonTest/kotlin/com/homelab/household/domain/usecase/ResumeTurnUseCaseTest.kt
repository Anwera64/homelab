package com.homelab.household.domain.usecase

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.model.ChatStreamEvent
import com.homelab.household.domain.repository.SessionRepository
import com.homelab.household.domain.usecase.impl.ResumeTurnUseCaseImpl
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ResumeTurnUseCaseTest {
    private val sessionRepo = mock<SessionRepository>()
    private val resumeTurn = ResumeTurnUseCaseImpl(sessionRepo)

    @Test
    fun `GIVEN no conversation WHEN a turn is resumed THEN it is refused`() {
        // GIVEN
        val sessionId = " "

        // WHEN
        val thrown = assertFailsWith<ValidationException> { resumeTurn(sessionId) }

        // THEN
        assertEquals("Session ID cannot be blank", thrown.message)
    }

    @Test
    fun `GIVEN a turn the phone stopped hearing WHEN it is resumed THEN the rest comes from the repository`() =
        runTest {
            // GIVEN
            val rest = listOf(ChatStreamEvent.Delta("lo"), ChatStreamEvent.StillWorking)
            every { sessionRepo.resumeTurn("s-1", "m1") } returns flowOf(*rest.toTypedArray())

            // WHEN
            val events = resumeTurn("s-1", afterAssistantMessageId = "m1").toList()

            // THEN
            assertEquals(rest, events)
        }
}
