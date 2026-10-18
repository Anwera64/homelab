package com.homelab.household.app.testing

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The hub as the members screens need it: who lives here, invites, PIN resets, changing a PIN,
 * removing someone and leaving, the profile's calendar, which writes agents do without asking, and
 * the memory page. Every body it was sent is kept, so a test can check what was asked.
 */
class FakeMembersHub {
    private val sent = mutableListOf<String>()
    val requests: List<String> get() = sent.toList()

    private var me = """{"id":"emma","full_name":"Emma Larsson","avatar_color":"#3C6E4E","is_admin":true,
        "is_active":true,"created_at":"2026-09-16T00:00:00Z"}"""
    private var household =
        listOf(
            """{"id":"emma","full_name":"Emma Larsson","avatar_color":"#3C6E4E","is_admin":true,"is_active":true,
            "created_at":"2026-09-16T00:00:00Z"}""",
            """{"id":"liam","full_name":"Liam","avatar_color":"#C05638","is_admin":false,"is_active":true,
            "created_at":"2026-09-16T00:00:00Z"}""",
        )
    private var calendar: String? = null

    /** How many times the phone asked the hub to forget the calendar. */
    var calendarsRemoved = 0
        private set
    private var write: suspend MockRequestHandleScope.() -> HttpResponseData = { json("""{"message":"done"}""") }
    private var offline = false

    /** The member's write settings as the hub keeps them: (tool, action) to automatic. */
    private val approvals =
        linkedMapOf(
            ("calendar_write" to "create") to false,
            ("calendar_write" to "update") to false,
            ("calendar_write" to "delete") to false,
            ("document_writer" to "create") to false,
            ("document_writer" to "append") to false,
            ("document_writer" to "replace") to false,
            ("remember" to "create") to false,
            ("forget" to "delete") to false,
        )
    private var refusesSettings = false

    /** What the agents remember, as the hub keeps it: id to (memory JSON, still active). */
    private val memories = linkedMapOf<String, Pair<String, Boolean>>()
    private var refusesRemoval = false

    /** Changes to check, as the hub keeps them: id to the card's JSON, who answers, and the answer so far. */
    private val changes = linkedMapOf<String, Triple<String, Boolean, String?>>()

    /** Emma's sentences waiting on Liam: id to (sentence, the stored memory's id, still waiting). */
    private val waiting = linkedMapOf<String, Triple<String, String, Boolean>>()

    /** Whether Emma's sentence [changeId] still waits on Liam, as the hub has it. */
    fun isWaiting(changeId: String): Boolean = waiting[changeId]?.third == true

    private var refusesAnswers = false
    private var tookBackBy: String? = null

    /** The answer the hub has for change [changeId] ("use_new", …), or null while it is open. */
    fun answerTo(changeId: String): String? = changes[changeId]?.third

    /** Whether the hub still has [memoryId] active. */
    fun isRemembered(memoryId: String): Boolean = memories[memoryId]?.second == true

    /** Whether agents add events without asking, as the hub now has it. */
    fun isAutomatic(
        tool: String,
        action: String,
    ): Boolean = approvals[tool to action] == true

    val engine: HttpClientEngine =
        MockEngine { request ->
            val path = request.url.encodedPath
            if (offline) return@MockEngine respond("", HttpStatusCode.ServiceUnavailable)
            if (request.method != HttpMethod.Get) {
                (request.body as? TextContent)?.let { sent += it.text }
            }
            when {
                path == "/api/v1/auth/me" -> {
                    json(me)
                }

                path == "/api/v1/auth/members" -> {
                    json(household.joinToString(prefix = "[", postfix = "]"))
                }

                path == "/api/v1/users" && request.method == HttpMethod.Get -> {
                    json(household.joinToString(prefix = "[", postfix = "]"))
                }

                path == "/api/v1/integrations/calendars/me" -> {
                    calendar?.let { json(it) }
                        ?: json("""{"detail":"No calendar configured."}""", HttpStatusCode.NotFound)
                }

                path == "/api/v1/users/me/tool-approvals" && request.method == HttpMethod.Get -> {
                    json(approvalsJson())
                }

                path == "/api/v1/users/me/tool-approvals" && request.method == HttpMethod.Put -> {
                    if (refusesSettings) return@MockEngine respond("", HttpStatusCode.ServiceUnavailable)
                    val body = (request.body as TextContent).text
                    val tool = Regex("\"tool\":\"([a-z_]+)\"").find(body)!!.groupValues[1]
                    val action = Regex("\"action\":\"([a-z]+)\"").find(body)!!.groupValues[1]
                    approvals[tool to action] = body.contains("\"auto\":true")
                    json(approvalsJson())
                }

                path == "/api/v1/memories/proposals" -> {
                    json(changesJson())
                }

                path.startsWith("/api/v1/memories/proposals/") && path.endsWith("/answer") -> {
                    if (refusesAnswers) return@MockEngine respond("", HttpStatusCode.ServiceUnavailable)
                    tookBackBy?.let { name ->
                        return@MockEngine json(
                            """{"detail":"$name took this back.","code":"change_withdrawn"}""",
                            HttpStatusCode.Conflict,
                        )
                    }
                    val id = path.removePrefix("/api/v1/memories/proposals/").removeSuffix("/answer")
                    val answer = Regex("\"answer\":\"([a-z_]+)\"").find(sent.last())!!.groupValues[1]
                    changes[id] = changes.getValue(id).copy(third = answer)
                    json("""{"id":"$id","memory_id":"s-$id","content":"x","state":"kept"}""")
                }

                path.startsWith("/api/v1/memories/proposals/") && path.endsWith("/undo") -> {
                    val id = path.removePrefix("/api/v1/memories/proposals/").removeSuffix("/undo")
                    changes[id] = changes.getValue(id).copy(third = null)
                    json("""{"id":"$id","memory_id":"s-$id","content":"x","state":"pending"}""")
                }

                path.startsWith("/api/v1/memories/proposals/") && request.method == HttpMethod.Delete -> {
                    val id = path.removePrefix("/api/v1/memories/proposals/")
                    waiting[id] = waiting.getValue(id).copy(third = false)
                    json("""{"id":"$id","memory_id":"x","content":"x","state":"withdrawn"}""")
                }

                path.startsWith("/api/v1/memories/proposals/") && path.endsWith("/restore") -> {
                    val id = path.removePrefix("/api/v1/memories/proposals/").removeSuffix("/restore")
                    waiting[id] = waiting.getValue(id).copy(third = true)
                    json("""{"id":"$id","memory_id":"x","content":"x","state":"pending"}""")
                }

                path == "/api/v1/memories/page" -> {
                    json(memoryPageJson())
                }

                path.startsWith("/api/v1/memories/") && request.method == HttpMethod.Delete -> {
                    if (refusesRemoval) return@MockEngine respond("", HttpStatusCode.ServiceUnavailable)
                    val id = path.removePrefix("/api/v1/memories/")
                    memories[id] = memories.getValue(id).first to false
                    respond("", HttpStatusCode.NoContent)
                }

                path.startsWith("/api/v1/memories/") && path.endsWith("/restore") -> {
                    val id = path.removePrefix("/api/v1/memories/").removeSuffix("/restore")
                    memories[id] = memories.getValue(id).first to true
                    json("""{"id":"$id","user_id":"emma","content":"x","is_active":true}""")
                }

                path == "/api/v1/integrations/calendars" && request.method == HttpMethod.Delete -> {
                    calendarsRemoved++
                    calendar = null
                    respond("", HttpStatusCode.NoContent)
                }

                else -> {
                    write()
                }
            }
        }

    /** Emma's iCloud calendar, which the hub last reached at [checkedAt] (UTC, written without a zone). */
    fun hasACalendar(checkedAt: String = "2026-09-26T20:04:00") {
        calendar =
            """{"id":"cal-1","user_id":"emma","provider":"apple_icloud","url":"https://caldav.icloud.com",
            "username":"emma@icloud.com","calendar_name":"Default","is_active":true,
            "created_at":"$checkedAt","updated_at":"$checkedAt"}"""
    }

    /** Emma's Google calendar, whose sign-in Google no longer accepts. */
    fun hasAGoogleCalendarNeedingSignInAgain() {
        calendar =
            """{"id":"cal-1","user_id":"emma","provider":"google_caldav",
            "url":"https://apidata.googleusercontent.com/caldav/v2/emma@gmail.com/events",
            "username":"emma@gmail.com","calendar_name":"Default","is_active":true,
            "auth_kind":"oauth","needs_reconnect":true,
            "created_at":"2026-09-26T20:04:00","updated_at":"2026-09-26T20:04:00"}"""
    }

    /** Agents already add events for Emma without asking. */
    fun addsEventsAutomatically() {
        approvals["calendar_write" to "create"] = true
    }

    /** The settings still list, but a change never reaches the hub. */
    fun refusesSettingChanges() {
        refusesSettings = true
    }

    private fun approvalsJson() =
        approvals.entries.joinToString(prefix = "[", postfix = "]") { (key, auto) ->
            val (tool, action) = key
            val always = action == "delete" || action == "replace"
            """{"tool":"$tool","action":"$action","auto":$auto,"always_asks":$always}"""
        }

    fun livesAlone() {
        household = listOf(household.first())
    }

    fun signedInAsAMember() {
        me = """{"id":"liam","full_name":"Liam","avatar_color":"#C05638","is_admin":false,"is_active":true,
            "created_at":"2026-09-16T00:00:00Z"}"""
    }

    fun makesInvites(
        code: String = "K7M2QP",
        invitedName: String = "Liam",
        secondsLeft: Int = 892,
    ) {
        write = {
            json(
                """{"code":"$code","invited_name":"$invitedName","is_admin":false,
                    "expires_at":"2026-09-16T00:15:00Z","expires_in_seconds":$secondsLeft}""",
                HttpStatusCode.Created,
            )
        }
    }

    fun approvesResets(
        code: String = "P4XN7T",
        secondsLeft: Int = 892,
    ) {
        write = {
            json(
                """{"code":"$code","expires_at":"2026-09-16T00:15:00Z","expires_in_seconds":$secondsLeft}""",
                HttpStatusCode.Created,
            )
        }
    }

    fun takesTheChange() {
        write = { json("""{"access_token":"fresh-token","token_type":"bearer","user":$me}""") }
    }

    fun doesIt() {
        write = { json("""{"message":"done"}""") }
    }

    fun refusesThePin(attemptsLeft: Int) {
        write = {
            json(
                """{"detail":"Wrong PIN","code":"wrong_pin","attempts_left":$attemptsLeft}""",
                HttpStatusCode.Forbidden,
            )
        }
    }

    fun saysTheNameIsTaken() {
        write = {
            json("""{"detail":"Someone already has that name.","code":"name_taken"}""", HttpStatusCode.Conflict)
        }
    }

    fun refusesTheOnlyAdmin() {
        write = {
            json(
                """{"detail":"You're the only admin, so the household can't lose you.","code":"sole_admin"}""",
                HttpStatusCode.Conflict,
            )
        }
    }

    /**
     * Something an agent remembers. [mine] is Emma's own; [household] puts it on the Household tab.
     * It was noted just now, so its row reads "today".
     */
    @OptIn(ExperimentalTime::class)
    fun remembers(
        id: String,
        content: String,
        household: Boolean = false,
        mine: Boolean = true,
        youAsked: Boolean = false,
        expiresAt: String? = null,
    ) {
        val createdAt = Clock.System.now().toString()
        val speaker = if (mine) "emma" else "liam"
        val name = if (mine) "Emma Larsson" else "Liam"
        memories[id] =
            """{"id":"$id","content":"$content","scope":"${if (household) "household" else "personal"}",
            "origin":"${if (youAsked) "tool" else "reflection"}","created_at":"$createdAt",
            "expires_at":${expiresAt?.let { "\"$it\"" } ?: "null"},"speaker_id":"$speaker","speaker_name":"$name",
            "is_mine":$mine,"agent_name":"Coordinator","agent_avatar":"🏡","can_remove":$mine}""" to true
    }

    /**
     * Emma asked an agent to remember [content] over Liam's memory [storedId], which must be remembered
     * already: it waits on him, so the page shows it beside his memory rather than in the list.
     */
    @OptIn(ExperimentalTime::class)
    fun waitsOnLiam(
        id: String,
        content: String,
        storedId: String,
    ) {
        waiting[id] = Triple(content, storedId, true)
    }

    /** The page lists, but ✕ never reaches the hub. */
    fun refusesMemoryRemoval() {
        refusesRemoval = true
    }

    private fun memoryPageJson(): String {
        val active = memories.values.filter { it.second }.map { it.first }
        val yours = active.filter { it.contains("\"scope\":\"personal\"") }
        val pairs = waiting.filterValues { it.third }
        val inCards = pairs.values.map { it.second }.toSet()
        val household =
            memories
                .filter { (id, memory) ->
                    memory.second && id !in inCards &&
                        memory.first.contains("\"scope\":\"household\"")
                }
                .values
                .map { it.first }
        val toCheck = changes.values.count { it.second && it.third == null }
        val cards =
            pairs.map { (id, change) ->
                """{"id":"$id","content":"${change.first}","agent_name":"Planner",
                "created_at":"${Clock.System.now()}","expires_at":null,"answerer_name":"Liam",
                "stored":${memories.getValue(change.second).first}}"""
            }
        return """{"yours":${yours.joinToString(prefix = "[", postfix = "]")},
            "household":${household.joinToString(prefix = "[", postfix = "]")},"to_check":$toCheck,
            "waiting":${cards.joinToString(prefix = "[", postfix = "]")}}"""
    }

    /**
     * An agent read [read] in a chat where [stored] is remembered. [mine] means Emma said the stored
     * memory and answers it; otherwise it waits for Liam. [title] is the chat's, shown only to its owner;
     * without one the card says whose chat it was.
     */
    @OptIn(ExperimentalTime::class)
    fun readsAChange(
        id: String,
        stored: String,
        read: String,
        mine: Boolean = true,
        household: Boolean = false,
        title: String? = "Dinner ideas",
    ) {
        val now = Clock.System.now().toString()
        val speaker = if (mine) "emma" else "liam"
        val name = if (mine) "Emma Larsson" else "Liam"
        val storedJson =
            """{"id":"s-$id","content":"$stored","scope":"${if (household) "household" else "personal"}",
            "origin":"reflection","created_at":"$now","expires_at":null,"speaker_id":"$speaker",
            "speaker_name":"$name","is_mine":$mine,"agent_name":"Planner","agent_avatar":"🗓️","can_remove":$mine}"""
        val firstName = name.substringBefore(" ")
        changes[id] =
            Triple(
                """{"id":"$id","content":"$read","stored":$storedJson,"can_answer":$mine,
                "answerer_name":"$firstName","source":{"title":${title?.let { "\"$it\"" } ?: "null"},
                "owner_name":"Liam","read_at":"$now"}}""",
                mine,
                null,
            )
    }

    /** [name] took their sentence back while it waited: an answer to it is refused, naming them. */
    fun changeIsTakenBackBy(name: String) {
        tookBackBy = name
    }

    /** Changes list, but no answer reaches the hub. */
    fun refusesAnswers() {
        refusesAnswers = true
    }

    private fun changesJson(): String {
        val open = changes.values.filter { it.second && it.third == null }.map { it.first }
        val waiting = changes.values.filter { !it.second }.map { it.first }
        return """{"to_check":${open.joinToString(prefix = "[", postfix = "]")},
            "waiting":${waiting.joinToString(prefix = "[", postfix = "]")}}"""
    }

    /** 503: a proxy in front of a hub that isn't running. */
    fun isOffline() {
        offline = true
    }

    private fun MockRequestHandleScope.json(
        content: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = respond(
        content = content,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )
}
