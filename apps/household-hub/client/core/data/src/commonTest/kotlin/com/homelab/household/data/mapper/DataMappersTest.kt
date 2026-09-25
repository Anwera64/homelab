package com.homelab.household.data.mapper

import com.homelab.household.data.dto.AgentReadDto
import com.homelab.household.data.dto.ChatMessageReadDto
import com.homelab.household.data.dto.GossipMilestoneDto
import com.homelab.household.data.dto.MemberProfileDto
import com.homelab.household.data.dto.MemoryReadDto
import com.homelab.household.data.dto.SessionReadDto
import com.homelab.household.data.dto.SpaceReadDto
import com.homelab.household.data.dto.UserReadDto
import com.homelab.household.domain.model.AnswerPart
import com.homelab.household.domain.model.Member
import com.homelab.household.domain.model.MemoryScope
import com.homelab.household.domain.model.MessageRole
import com.homelab.household.domain.model.SpaceType
import com.homelab.household.domain.model.ToolFailureReason
import com.homelab.household.domain.model.ToolSource
import com.homelab.household.domain.model.ToolSummary
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class DataMappersTest {
    @Test
    fun user_mapper_converts_dto_to_domain() {
        val dto =
            UserReadDto(
                id = "user-1",
                full_name = "Alice Doe",
                is_admin = true,
                is_active = true,
                personal_space_id = "space-1",
                avatar_color = "#C05638",
                created_at = "2026-09-08T00:00:00Z",
            )
        val user = UserDataMapper.toDomain(dto)

        assertEquals("user-1", user.id)
        assertEquals("Alice Doe", user.fullName)
        assertEquals("#C05638", user.avatarColor)
        assertEquals(true, user.isAdmin)
    }

    @Test
    fun a_member_profile_becomes_a_member() {
        val member =
            UserDataMapper.toMember(
                MemberProfileDto(id = "emma", full_name = "Emma", avatar_color = "#3C6E4E"),
            )

        assertEquals(Member(id = "emma", name = "Emma", avatarColor = "#3C6E4E"), member)
    }

    @Test
    fun session_mapper_converts_dto_to_domain() {
        val dto =
            SessionReadDto(
                id = "s-1",
                user_id = "u-1",
                agent_id = "a-1",
                title = "Test Chat",
                is_secret = true,
                is_archived = false,
                created_at = "2026-09-08T01:00:00Z",
                updated_at = "2026-09-08T01:00:00Z",
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
        val dto =
            ChatMessageReadDto(
                id = "m-1",
                session_id = "s-1",
                role = "assistant",
                content = "Hello there",
                created_at = "2026-09-08T01:01:00Z",
            )
        val msg = ChatMessageDataMapper.toDomain(dto)

        assertEquals("m-1", msg.id)
        assertEquals("s-1", msg.sessionId)
        assertEquals(MessageRole.ASSISTANT, msg.role)
        assertEquals("Hello there", msg.content)
    }

    @Test
    fun `GIVEN an answer saved with its parts WHEN mapped THEN the parts keep their order`() {
        val dto =
            Json { ignoreUnknownKeys = true }.decodeFromString<ChatMessageReadDto>(
                """
                {"id": "m-2", "session_id": "s-1", "role": "assistant", "content": "Let me check.\n\nIt stays dry.",
                 "metadata_json": {"tools_executed": [], "parts": [
                    {"type": "thought", "seconds": 6},
                    {"type": "text", "content": "Let me check."},
                    {"type": "tool", "tool": "web_search", "success": true},
                    {"type": "tool", "tool": "calendar_read", "success": false},
                    {"type": "text", "content": "It stays dry."}
                 ]}}
                """.trimIndent(),
            )

        val msg = ChatMessageDataMapper.toDomain(dto)

        assertEquals(
            listOf(
                AnswerPart.Thought(6),
                AnswerPart.Text("Let me check."),
                AnswerPart.ToolDone("web_search"),
                AnswerPart.ToolFailed("calendar_read"),
                AnswerPart.Text("It stays dry."),
            ),
            msg.parts,
        )
    }

    @Test
    fun `GIVEN an answer saved before parts were kept WHEN mapped THEN it has none`() {
        val dto =
            Json { ignoreUnknownKeys = true }.decodeFromString<ChatMessageReadDto>(
                """{"id": "m-1", "session_id": "s-1", "role": "assistant", "content": "Hi", "metadata_json": {"tools_executed": []}}""",
            )

        assertEquals(emptyList(), ChatMessageDataMapper.toDomain(dto).parts)
    }

    @Test
    fun `GIVEN a part the phone does not know WHEN mapped THEN it is skipped and the rest kept`() {
        val dto =
            Json { ignoreUnknownKeys = true }.decodeFromString<ChatMessageReadDto>(
                """
                {"id": "m-1", "session_id": "s-1", "role": "assistant", "content": "Hi",
                 "metadata_json": {"parts": [{"type": "image", "url": "x"}, {"type": "text", "content": "Hi"}]}}
                """.trimIndent(),
            )

        assertEquals(listOf(AnswerPart.Text("Hi")), ChatMessageDataMapper.toDomain(dto).parts)
    }

    private fun partsOf(partsJson: String) =
        ChatMessageDataMapper
            .toDomain(
                Json { ignoreUnknownKeys = true }.decodeFromString<ChatMessageReadDto>(
                    """{"id": "m-1", "session_id": "s-1", "role": "assistant", "content": "x", "metadata_json": {"parts": $partsJson}}""",
                ),
            ).parts

    @Test
    fun `GIVEN a search saved with its summary WHEN mapped THEN the part carries its query and results`() {
        val parts =
            partsOf(
                """[{"type": "tool", "tool": "searxng_search", "success": true,
                    "summary": {"query": "dinner Gràcia", "count": 2, "sources": [
                        {"title": "The best restaurants in Gràcia", "url": "https://www.timeout.com/gracia"},
                        {"title": "Menu and opening hours", "url": "https://lapubilla.cat/"}]}}]""",
            )

        assertEquals(
            listOf(
                AnswerPart.ToolDone(
                    "searxng_search",
                    ToolSummary(
                        query = "dinner Gràcia",
                        count = 2,
                        sources =
                            listOf(
                                ToolSource("The best restaurants in Gràcia", "https://www.timeout.com/gracia"),
                                ToolSource("Menu and opening hours", "https://lapubilla.cat/"),
                            ),
                    ),
                ),
            ),
            parts,
        )
    }

    @Test
    fun `GIVEN a failed read saved with its reason WHEN mapped THEN the part says why and which page`() {
        val parts =
            partsOf(
                """[{"type": "tool", "tool": "read_page", "success": false,
                    "summary": {"reason": "blocked", "sources": [{"title": "", "url": "https://www.scmp.com/news"}]}}]""",
            )

        assertEquals(
            listOf(
                AnswerPart.ToolFailed(
                    "read_page",
                    ToolSummary(
                        reason = ToolFailureReason.Blocked,
                        sources = listOf(ToolSource("", "https://www.scmp.com/news")),
                    ),
                ),
            ),
            parts,
        )
    }

    @Test
    fun `GIVEN a reason this phone does not know WHEN mapped THEN it is unknown`() {
        val parts =
            partsOf(
                """[{"type": "tool", "tool": "read_page", "success": false, "summary": {"reason": "moon_phase"}}]""",
            )

        assertEquals(listOf(AnswerPart.ToolFailed("read_page", ToolSummary(reason = ToolFailureReason.Unknown))), parts)
    }

    @Test
    fun `GIVEN an added event saved with its title WHEN mapped THEN the part names it`() {
        val parts =
            partsOf(
                """[{"type": "tool", "tool": "calendar_write", "success": true, "summary": {"title": "Dinner together"}}]""",
            )

        assertEquals(listOf(AnswerPart.ToolDone("calendar_write", ToolSummary(title = "Dinner together"))), parts)
    }

    @Test
    fun `GIVEN a malformed summary WHEN mapped THEN the part is kept without one`() {
        val parts =
            partsOf(
                """[{"type": "tool", "tool": "searxng_search", "success": true, "summary": "nonsense"},
                    {"type": "tool", "tool": "read_page", "success": true, "summary": {"sources": "nope", "count": "many"}}]""",
            )

        assertEquals(
            listOf(AnswerPart.ToolDone("searxng_search"), AnswerPart.ToolDone("read_page", ToolSummary())),
            parts,
        )
    }

    @Test
    fun `GIVEN a source that is not a web link WHEN mapped THEN it never becomes something to tap`() {
        val parts =
            partsOf(
                """[{"type": "tool", "tool": "searxng_search", "success": true, "summary": {"query": "q", "count": 3, "sources": [
                    {"title": "Fine", "url": "https://example.org/"},
                    {"title": "Script", "url": "javascript:alert(1)"},
                    {"title": "No link"}]}}]""",
            )

        val summary = (parts.single() as AnswerPart.ToolDone).summary
        assertEquals(listOf(ToolSource("Fine", "https://example.org/")), summary?.sources)
    }

    @Test
    fun space_mapper_converts_dto_to_domain() {
        val dto =
            SpaceReadDto(
                id = "sp-1",
                name = "Personal Space",
                type = "personal",
                owner_id = "u-1",
                settings = mapOf("theme" to "dark"),
                created_at = "2026-09-08T00:00:00Z",
            )
        val space = SpaceDataMapper.toDomain(dto)

        assertEquals("sp-1", space.id)
        assertEquals(SpaceType.PERSONAL, space.type)
        assertEquals("dark", space.settings["theme"])
    }

    @Test
    fun agent_mapper_converts_dto_to_domain() {
        val dto =
            AgentReadDto(
                id = "ag-1",
                slug = "assistant",
                name = "Life Coordinator",
                description = "Coordinates tasks",
                avatar = "🤖",
                system_prompt = "You are an assistant",
                temperature = 0.7f,
                top_p = 0.9f,
                tool_permissions = listOf("calendar_read"),
                is_builtin = true,
                is_active = true,
            )
        val agent = AgentDataMapper.toDomain(dto)

        assertEquals("ag-1", agent.id)
        assertEquals("assistant", agent.slug)
        assertEquals("Life Coordinator", agent.name)
        assertEquals(1, agent.toolPermissions.size)
    }

    @Test
    fun memory_mapper_converts_dto_to_domain() {
        val dto =
            MemoryReadDto(
                id = "mem-1",
                user_id = "u-1",
                agent_id = "ag-1",
                content = "Vegetarian",
                category = "preference",
                scope = "personal",
                confidence = 0.9f,
                is_active = true,
            )
        val mem = MemoryDataMapper.toDomain(dto)

        assertEquals("mem-1", mem.id)
        assertEquals("Vegetarian", mem.content)
        assertEquals(MemoryScope.PERSONAL, mem.scope)
    }

    @Test
    fun gossip_mapper_converts_dto_to_domain() {
        val dto =
            GossipMilestoneDto(
                id = "gm-1",
                source_user_id = "u-1",
                source_username = "alice",
                reporting_agent_id = "ag-1",
                reporting_agent_name = "Life Coordinator",
                target_scope = "household",
                category = "event",
                summary = "Trip to Madrid",
                is_active = true,
                created_at = "2026-09-08T02:00:00Z",
            )
        val milestone = GossipDataMapper.toDomain(dto)

        assertEquals("gm-1", milestone.id)
        assertEquals("alice", milestone.sourceUsername)
        assertEquals("Trip to Madrid", milestone.summary)
    }
}
