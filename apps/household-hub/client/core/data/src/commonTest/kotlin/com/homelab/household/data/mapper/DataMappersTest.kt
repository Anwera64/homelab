package com.homelab.household.data.mapper

import com.homelab.household.data.dto.AgentReadDto
import com.homelab.household.data.dto.ChatMessageReadDto
import com.homelab.household.data.dto.MemberProfileDto
import com.homelab.household.data.dto.MemoryPageItemDto
import com.homelab.household.data.dto.SessionReadDto
import com.homelab.household.data.dto.SpaceReadDto
import com.homelab.household.data.dto.UserReadDto
import com.homelab.household.domain.model.AnswerPart
import com.homelab.household.domain.model.Member
import com.homelab.household.domain.model.EventTime
import com.homelab.household.domain.model.MemoryOrigin
import com.homelab.household.domain.model.MemoryScope
import com.homelab.household.domain.model.MessageRole
import com.homelab.household.domain.model.SpaceType
import com.homelab.household.domain.model.ToolAction
import com.homelab.household.domain.model.ToolFailureReason
import com.homelab.household.domain.model.ToolSource
import com.homelab.household.domain.model.ToolSummary
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class DataMappersTest {
    @Test
    fun `GIVEN a member record from the hub WHEN it is mapped THEN the user keeps its id and name and colour and admin flag`() {
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
    fun `GIVEN a member profile from the hub WHEN it is mapped THEN it becomes a member with the same id and name and colour`() {
        val member =
            UserDataMapper.toMember(
                MemberProfileDto(id = "emma", full_name = "Emma", avatar_color = "#3C6E4E"),
            )

        assertEquals(Member(id = "emma", name = "Emma", avatarColor = "#3C6E4E"), member)
    }

    @Test
    fun `GIVEN a secret conversation record from the hub WHEN it is mapped THEN the session keeps its ids and title and secret flag`() {
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
    fun `GIVEN an assistant message record from the hub WHEN it is mapped THEN the message keeps its ids and role and text`() {
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
    fun `GIVEN a throttled search saved with its reason WHEN mapped THEN the part says so`() {
        val parts =
            partsOf(
                """[{"type": "tool", "tool": "searxng_search", "success": false,
                    "summary": {"query": "q", "reason": "throttled"}}]""",
            )

        assertEquals(
            listOf(
                AnswerPart.ToolFailed(
                    "searxng_search",
                    ToolSummary(query = "q", reason = ToolFailureReason.Throttled),
                ),
            ),
            parts,
        )
    }

    @Test
    fun `GIVEN a removal saved with its action WHEN mapped THEN the part says it was a removal`() {
        val parts =
            partsOf(
                """[{"type": "tool", "tool": "calendar_write", "success": true,
                    "summary": {"action": "delete", "title": "Print shop cutoff"}}]""",
            )

        assertEquals(
            listOf(
                AnswerPart.ToolDone(
                    "calendar_write",
                    ToolSummary(action = ToolAction.Delete, title = "Print shop cutoff"),
                ),
            ),
            parts,
        )
    }

    @Test
    fun `GIVEN an add that ran without a card WHEN mapped THEN the part says it was automatic`() {
        val parts =
            partsOf(
                """[{"type": "tool", "tool": "calendar_write", "success": true, "auto": true,
                    "summary": {"action": "create", "title": "Print shop cutoff"}}]""",
            )

        assertEquals(
            listOf(
                AnswerPart.ToolDone(
                    "calendar_write",
                    ToolSummary(action = ToolAction.Create, title = "Print shop cutoff"),
                    automatic = true,
                ),
            ),
            parts,
        )
    }

    @Test
    fun `GIVEN each write action the hub names WHEN mapped THEN each is its own action`() {
        val actions =
            mapOf(
                "create" to ToolAction.Create,
                "update" to ToolAction.Update,
                "delete" to ToolAction.Delete,
                "append" to ToolAction.Append,
                "replace" to ToolAction.Replace,
            )

        actions.forEach { (code, action) ->
            val parts =
                partsOf(
                    """[{"type": "tool", "tool": "document_writer", "success": true, "summary": {"action": "$code"}}]""",
                )
            assertEquals(listOf(AnswerPart.ToolDone("document_writer", ToolSummary(action = action))), parts, code)
        }
    }

    @Test
    fun `GIVEN an action this phone does not know WHEN mapped THEN it is left out rather than guessed`() {
        val parts =
            partsOf(
                """[{"type": "tool", "tool": "calendar_write", "success": true, "summary": {"action": "obliterate"}}]""",
            )

        assertEquals(listOf(AnswerPart.ToolDone("calendar_write", ToolSummary())), parts)
    }

    @Test
    fun `GIVEN a calendar step that failed on its sign-in or on having no calendar WHEN mapped THEN the part says which`() {
        val reasons =
            mapOf(
                "calendar_rejected" to ToolFailureReason.CalendarRejected,
                "calendar_not_connected" to ToolFailureReason.CalendarNotConnected,
            )

        reasons.forEach { (code, reason) ->
            val parts =
                partsOf(
                    """[{"type": "tool", "tool": "calendar_write", "success": false,
                        "summary": {"action": "create", "reason": "$code"}}]""",
                )
            val expected =
                AnswerPart.ToolFailed(
                    "calendar_write",
                    ToolSummary(action = ToolAction.Create, reason = reason),
                )
            assertEquals(listOf(expected), parts, code)
        }
    }

    @Test
    fun `GIVEN a calendar step the hub marked fixed WHEN mapped THEN the part says it was fixed`() {
        val parts =
            partsOf(
                """[{"type": "tool", "tool": "calendar_write", "success": false,
                    "summary": {"action": "create", "reason": "calendar_rejected", "fixed": true}}]""",
            )

        val expected =
            AnswerPart.ToolFailed(
                "calendar_write",
                ToolSummary(action = ToolAction.Create, reason = ToolFailureReason.CalendarRejected, fixed = true),
            )
        assertEquals(listOf(expected), parts)
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
    fun `GIVEN a personal space record from the hub WHEN it is mapped THEN the space keeps its id and type and settings`() {
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
    fun `GIVEN an agent record from the hub WHEN it is mapped THEN the agent keeps its id and slug and name and tool permissions`() {
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
    fun `GIVEN a memory page row WHEN it is mapped THEN its times and origin and expiry come across`() {
        val dto =
            MemoryPageItemDto(
                id = "mem-1",
                content = "Studio Jury on Friday 17 October, 09:00.",
                scope = "personal",
                origin = "tool",
                created_at = "2026-10-13T09:00:00.123456",
                expires_at = "2026-10-17T10:00:00+00:00",
                speaker_id = "emma",
                speaker_name = "Emma",
                is_mine = true,
                agent_name = "Researcher",
                agent_avatar = "📚",
                can_remove = true,
            )

        val memory = MemoryDataMapper.toDomain(dto)

        // The hub's SQLite hands back times without a zone; every one it writes is UTC.
        assertEquals(Instant.parse("2026-10-13T09:00:00.123456Z"), memory.createdAt)
        assertEquals(Instant.parse("2026-10-17T10:00:00Z"), memory.expiresAt)
        assertEquals(MemoryOrigin.Tool, memory.origin)
        assertEquals(MemoryScope.PERSONAL, memory.scope)
        assertEquals("Researcher", memory.agentName)
    }

    @Test
    fun `GIVEN an origin this phone doesn't know WHEN it is mapped THEN it reads as reflection`() {
        val dto =
            MemoryPageItemDto(
                id = "mem-1",
                content = "x",
                scope = "household",
                origin = "imported",
                created_at = "2026-10-13T09:00:00Z",
                speaker_id = "liam",
                speaker_name = "Liam",
                agent_name = "an agent",
                agent_avatar = "🤖",
            )

        val memory = MemoryDataMapper.toDomain(dto)

        assertEquals(MemoryOrigin.Reflection, memory.origin)
        assertEquals(MemoryScope.HOUSEHOLD, memory.scope)
        assertEquals(false, memory.canRemove)
    }

    @Test
    fun `GIVEN a memory rebuilt from its event WHEN it is mapped THEN its start and where it moved from come across`() {
        val dto =
            MemoryPageItemDto(
                id = "mem-1",
                content = "Dinner together",
                created_at = "2026-10-13T09:00:00Z",
                speaker_id = "emma",
                agent_name = "Planner",
                agent_avatar = "🗓",
                event_starts_at = "2026-10-19T17:30:00",
                time_from_event = "timed",
                moved_from = "2026-10-18T18:00:00",
            )

        val memory = MemoryDataMapper.toDomain(dto)

        assertEquals(EventTime(startsAt = Instant.parse("2026-10-19T17:30:00Z"), allDay = false), memory.eventTime)
        assertEquals(Instant.parse("2026-10-18T18:00:00Z"), memory.movedFrom)
    }

    @Test
    fun `GIVEN an all-day event memory WHEN it is mapped THEN its time is a day`() {
        val dto =
            MemoryPageItemDto(
                id = "mem-1",
                content = "Studio jury",
                created_at = "2026-10-13T09:00:00Z",
                speaker_id = "emma",
                agent_name = "Planner",
                agent_avatar = "🗓",
                event_starts_at = "2026-10-24T00:00:00Z",
                time_from_event = "all_day",
            )

        assertEquals(true, MemoryDataMapper.toDomain(dto).eventTime?.allDay)
    }

    @Test
    fun `GIVEN a memory in the model's own words WHEN it is mapped THEN it has no event time`() {
        val dto =
            MemoryPageItemDto(
                id = "mem-1",
                content = "Dinner on Saturday at 20:00",
                created_at = "2026-10-13T09:00:00Z",
                speaker_id = "emma",
                agent_name = "Planner",
                agent_avatar = "🗓",
                event_starts_at = "2026-10-19T17:30:00Z",
                time_from_event = "sometime",
            )

        val memory = MemoryDataMapper.toDomain(dto)

        assertEquals(null, memory.eventTime)
        assertEquals(null, memory.movedFrom)
    }
}
