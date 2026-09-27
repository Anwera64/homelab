package com.homelab.household.app.screens.calendarconnect

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf

/**
 * The hub as connecting a calendar needs it: one POST, answered however the test says. Every body
 * it was sent is kept, so a test can check what reached it.
 */
class FakeCalendarHub {
    private val sent = mutableListOf<String>()
    val requests: List<String> get() = sent.toList()

    private var answer: suspend MockRequestHandleScope.() -> HttpResponseData = { connected() }

    val engine: HttpClientEngine =
        MockEngine { request ->
            (request.body as? TextContent)?.let { sent += it.text }
            when (request.url.encodedPath) {
                "/api/v1/integrations/calendars" -> answer()
                else -> respond("", HttpStatusCode.NotFound)
            }
        }

    /** The calendar answered and refused the password: nothing was kept. */
    fun rejectsThePassword() {
        answer = { json("""{"detail":"refused","code":"calendar_rejected"}""", HttpStatusCode.BadRequest) }
    }

    /** The hub couldn't reach the calendar's server: nothing was kept. */
    fun cannotReachTheServer() {
        answer =
            { json("""{"detail":"could not be reached","code":"calendar_unreachable"}""", HttpStatusCode.BadRequest) }
    }

    private fun MockRequestHandleScope.connected() =
        json(
            """{"id":"cal-1","user_id":"emma","provider":"apple_icloud","url":"https://caldav.icloud.com",
            "username":"emma@icloud.com","calendar_name":"Default","is_active":true,
            "created_at":"2026-09-26T20:04:00","updated_at":"2026-09-26T20:04:00"}""",
            HttpStatusCode.Created,
        )

    private fun MockRequestHandleScope.json(
        content: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = respond(
        content = content,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )
}
