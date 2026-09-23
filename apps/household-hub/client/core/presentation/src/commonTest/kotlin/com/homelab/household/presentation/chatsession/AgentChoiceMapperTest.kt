package com.homelab.household.presentation.chatsession

import com.homelab.household.domain.model.AgentPersonality
import com.homelab.household.domain.model.User
import kotlin.test.Test
import kotlin.test.assertEquals

class AgentChoiceMapperTest {
    private val me = "user-emma"
    private val liam = User(id = "user-liam", fullName = "Liam Doyle", isAdmin = false, isActive = true)
    private val membersById = mapOf(liam.id to liam)

    private fun agent(
        isBuiltin: Boolean = false,
        ownerId: String? = null,
    ) = AgentPersonality(
        id = "agent-1",
        slug = "researcher",
        name = "Academic Researcher",
        description = "Papers, references, reading long PDFs properly.",
        avatar = "📚",
        systemPrompt = "",
        toolPermissions = listOf("searxng_search", "pdf_reader"),
        isBuiltin = isBuiltin,
        ownerId = ownerId,
    )

    @Test
    fun `GIVEN a built-in agent WHEN mapped THEN it is marked built in and keeps its words and tools`() {
        val choice = agent(isBuiltin = true, ownerId = me).toAgentChoice(me, membersById)

        assertEquals(
            AgentChoice(
                id = "agent-1",
                name = "Academic Researcher",
                avatar = "📚",
                tagline = "Papers, references, reading long PDFs properly.",
                owner = AgentOwner.BuiltIn,
                tools = listOf("searxng_search", "pdf_reader"),
            ),
            choice,
        )
    }

    @Test
    fun `GIVEN an agent I made WHEN mapped THEN it is mine`() {
        assertEquals(AgentOwner.Yours, agent(ownerId = me).toAgentChoice(me, membersById).owner)
    }

    @Test
    fun `GIVEN another member's agent WHEN mapped THEN it carries their first name`() {
        assertEquals(AgentOwner.Member("Liam"), agent(ownerId = liam.id).toAgentChoice(me, membersById).owner)
    }

    @Test
    fun `GIVEN an owner the members list does not name WHEN mapped THEN the owner is unknown`() {
        assertEquals(AgentOwner.Unknown, agent(ownerId = "user-gone").toAgentChoice(me, membersById).owner)
    }

    @Test
    fun `GIVEN nobody is signed in WHEN mapped THEN nothing is called mine`() {
        assertEquals(AgentOwner.Unknown, agent(ownerId = null).toAgentChoice(null, membersById).owner)
    }
}
