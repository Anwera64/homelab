package com.homelab.household.data.repository

import com.homelab.household.data.datasource.remote.`interface`.AgentRemoteDataSource
import com.homelab.household.data.dto.AgentCreateDto
import com.homelab.household.data.dto.AgentReadDto
import com.homelab.household.data.dto.AgentUpdateDto
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.AgentPersonality
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The agents repository maps DTOs to `AgentPersonality` and back. Creating or updating one is the
 * only place it builds a DTO of its own — everything else is a straight pass to the data source.
 */
class AgentRepositoryTest {
    private val llamaDto =
        AgentReadDto(
            id = "a-1",
            slug = "llama",
            name = "Llama",
            description = "General helper",
            avatar = "🦙",
            system_prompt = "You are helpful.",
            temperature = 0.7f,
            top_p = 0.9f,
            tool_permissions = listOf("search"),
            is_builtin = false,
            is_active = true,
            owner_id = "emma",
            created_at = "2026-09-13T00:00:00Z",
        )

    private val llama =
        AgentPersonality(
            id = "a-1",
            slug = "llama",
            name = "Llama",
            description = "General helper",
            avatar = "🦙",
            systemPrompt = "You are helpful.",
            temperature = 0.7f,
            topP = 0.9f,
            toolPermissions = listOf("search"),
            isBuiltin = false,
            isActive = true,
            ownerId = "emma",
            createdAt = "2026-09-13T00:00:00Z",
        )

    private fun repository(remote: AgentRemoteDataSource) = AgentRepositoryImpl(remote = remote)

    // ---- listing --------------------------------------------------------------

    @Test
    fun `GIVEN the hub lists one agent WHEN its personalities are asked for THEN it is mapped to an agent personality`() =
        runTest {
            // GIVEN
            val remote = mock<AgentRemoteDataSource>()
            everySuspend { remote.listAgents() } returns listOf(llamaDto)

            // WHEN
            val agents = repository(remote).listAgents()

            // THEN
            assertEquals(listOf(llama), agents)
        }

    @Test
    fun `GIVEN the hub cannot be reached WHEN the household's agents are asked for THEN the failure reaches the caller`() =
        runTest {
            // GIVEN
            val remote = mock<AgentRemoteDataSource>()
            everySuspend { remote.listAgents() } throws ServerOfflineException()

            // WHEN / THEN
            assertFailsWith<ServerOfflineException> { repository(remote).listAgents() }
        }

    // ---- getAgent ---------------------------------------------------------

    @Test
    fun `GIVEN one agent's id WHEN it is asked for by itself THEN it is mapped to an agent personality`() =
        runTest {
            // GIVEN
            val remote = mock<AgentRemoteDataSource>()
            everySuspend { remote.getAgent("a-1") } returns llamaDto

            // WHEN
            val agent = repository(remote).getAgent("a-1")

            // THEN
            assertEquals(llama, agent)
        }

    // ---- createAgent ------------------------------------------------------

    @Test
    fun `GIVEN a new agent's personality WHEN it is created THEN it is sent as a create request and the stored agent comes back mapped`() =
        runTest {
            // GIVEN
            val remote = mock<AgentRemoteDataSource>()
            val expectedCreate =
                AgentCreateDto(
                    slug = "llama",
                    name = "Llama",
                    description = "General helper",
                    avatar = "🦙",
                    system_prompt = "You are helpful.",
                    temperature = 0.7f,
                    top_p = 0.9f,
                    tool_permissions = listOf("search"),
                )
            everySuspend { remote.createAgent(expectedCreate) } returns llamaDto

            // WHEN
            val agent = repository(remote).createAgent(llama)

            // THEN
            assertEquals(llama, agent)
            verifySuspend(VerifyMode.exactly(1)) { remote.createAgent(expectedCreate) }
        }

    // ---- updateAgent --------------------------------------------------------

    @Test
    fun `GIVEN changes to an agent's personality WHEN it is updated THEN they are sent to that agent's own id and the result comes back mapped`() =
        runTest {
            // GIVEN
            val remote = mock<AgentRemoteDataSource>()
            val expectedUpdate =
                AgentUpdateDto(
                    name = "Llama",
                    description = "General helper",
                    avatar = "🦙",
                    system_prompt = "You are helpful.",
                    temperature = 0.7f,
                    top_p = 0.9f,
                    tool_permissions = listOf("search"),
                    is_active = true,
                )
            everySuspend { remote.updateAgent("a-1", expectedUpdate) } returns llamaDto

            // WHEN
            val agent = repository(remote).updateAgent(llama)

            // THEN
            assertEquals(llama, agent)
            verifySuspend(VerifyMode.exactly(1)) { remote.updateAgent("a-1", expectedUpdate) }
        }

    // ---- deleteAgent --------------------------------------------------------

    @Test
    fun `GIVEN an agent an admin no longer wants WHEN it is deleted THEN the data source is asked to remove it`() =
        runTest {
            // GIVEN
            val remote = mock<AgentRemoteDataSource>()
            everySuspend { remote.deleteAgent("a-1") } returns Unit

            // WHEN
            repository(remote).deleteAgent("a-1")

            // THEN
            verifySuspend(VerifyMode.exactly(1)) { remote.deleteAgent("a-1") }
        }

    // ---- restoreAgent -------------------------------------------------------

    @Test
    fun `GIVEN a deleted agent WHEN it is restored THEN it is mapped to an agent personality`() =
        runTest {
            // GIVEN
            val remote = mock<AgentRemoteDataSource>()
            everySuspend { remote.restoreAgent("a-1") } returns llamaDto

            // WHEN
            val agent = repository(remote).restoreAgent("a-1")

            // THEN
            assertEquals(llama, agent)
        }
}
