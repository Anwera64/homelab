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

/**
 * The hub as the members screens need it: who lives here, invites, PIN resets, changing a PIN,
 * removing someone and leaving, the profile's calendar, and which writes agents do without asking. Every body it was sent is kept, so a
 * test can check what was asked.
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
        )
    private var refusesSettings = false

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
