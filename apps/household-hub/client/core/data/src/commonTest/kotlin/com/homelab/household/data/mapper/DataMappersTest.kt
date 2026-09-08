package com.homelab.household.data.mapper

import com.homelab.household.data.dto.AgentReadDto
import com.homelab.household.data.dto.ChatMessageReadDto
import com.homelab.household.data.dto.GossipMilestoneDto
import com.homelab.household.data.dto.MemoryReadDto
import com.homelab.household.data.dto.SessionReadDto
import com.homelab.household.data.dto.SpaceReadDto
import com.homelab.household.data.dto.UserReadDto
import com.homelab.household.domain.model.MemoryScope
import com.homelab.household.domain.model.MessageRole
import com.homelab.household.domain.model.SpaceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DataMappersTest {

    @Test
    fun user_mapper_converts_dto_to_domain() {
        val dto = UserReadDto(
            id = "user-1",
            username = "alice",
            email = "alice@homelab.local",
            full_name = "Alice Doe",
            is_admin = true,
            is_active = true,
            personal_space_id = "space-1",
            avatar_color = "#4F46E5",
            created_at = "2026-09-08T00:00:00Z"
        )
        val user = UserDataMapper.toDomain(dto)

        assertEquals("user-1", user.id)
        assertEquals("alice", user.username)
        assertEquals("Alice Doe", user.fullName)
        assertEquals(true, user.isAdmin)
    }

    @Test
    fun session_mapper_converts_dto_to_domain() {
        val dto = SessionReadDto(
            id = "s-1",
            user_id = "u-1",
            agent_id = "a-1",
            title = "Test Chat",
            is_secret = true,
            is_archived = false,
            created_at = "2026-09-08T01:00:00Z",
            updated_at = "2026-09-08T01:00:00Z"
        )
        val session = SessionDataMapper.toDomain(dto)

        assertEquals("s-1", session.id)
        assertEquals("u-1", session.userId)
        assertEquals("a-1", session.agentId)
        assertEquals("Test Chat", session.title)
        assertEquals(true, session.isSecret)
    }

    @Test
    fun message_mapper_converts_dto_to_domain() {
        val dto = ChatMessageReadDto(
            id = "m-1",
            session_id = "s-1",
            role = "assistant",
            content = "Hello there",
            created_at = "2026-09-08T01:01:00Z"
        )
        val msg = ChatMessageDataMapper.toDomain(dto)

        assertEquals("m-1", msg.id)
        assertEquals("s-1", msg.sessionId)
        assertEquals(MessageRole.ASSISTANT, msg.role)
        assertEquals("Hello there", msg.content)
    }

    @Test
    fun space_mapper_converts_dto_to_domain() {
        val dto = SpaceReadDto(
            id = "sp-1",
            name = "Personal Space",
            type = "personal",
            owner_id = "u-1",
            settings = mapOf("theme" to "dark"),
            created_at = "2026-09-08T00:00:00Z"
        )
        val space = SpaceDataMapper.toDomain(dto)

        assertEquals("sp-1", space.id)
        assertEquals(SpaceType.PERSONAL, space.type)
        assertEquals("dark", space.settings["theme"])
    }

    @Test
    fun agent_mapper_converts_dto_to_domain() {
        val dto = AgentReadDto(
            id = "ag-1",
            slug = "assistant",
            name = "Life Coordinator",
            description = "Coordinates tasks",
            avatar = "🤖",
            system_prompt = "You are an assistant",
            model_alias = "qwen3:14b",
            temperature = 0.7f,
            top_p = 0.9f,
            tool_permissions = listOf("calendar_read"),
            is_builtin = true,
            is_active = true
        )
        val agent = AgentDataMapper.toDomain(dto)

        assertEquals("ag-1", agent.id)
        assertEquals("assistant", agent.slug)
        assertEquals("Life Coordinator", agent.name)
        assertEquals(1, agent.toolPermissions.size)
    }

    @Test
    fun memory_mapper_converts_dto_to_domain() {
        val dto = MemoryReadDto(
            id = "mem-1",
            user_id = "u-1",
            agent_id = "ag-1",
            content = "Vegetarian",
            category = "preference",
            scope = "personal",
            confidence = 0.9f,
            is_active = true
        )
        val mem = MemoryDataMapper.toDomain(dto)

        assertEquals("mem-1", mem.id)
        assertEquals("Vegetarian", mem.content)
        assertEquals(MemoryScope.PERSONAL, mem.scope)
    }

    @Test
    fun gossip_mapper_converts_dto_to_domain() {
        val dto = GossipMilestoneDto(
            id = "gm-1",
            source_user_id = "u-1",
            source_username = "alice",
            reporting_agent_id = "ag-1",
            reporting_agent_name = "Life Coordinator",
            target_scope = "household",
            category = "event",
            summary = "Trip to Madrid",
            is_active = true,
            created_at = "2026-09-08T02:00:00Z"
        )
        val milestone = GossipDataMapper.toDomain(dto)

        assertEquals("gm-1", milestone.id)
        assertEquals("alice", milestone.sourceUsername)
        assertEquals("Trip to Madrid", milestone.summary)
    }
}
