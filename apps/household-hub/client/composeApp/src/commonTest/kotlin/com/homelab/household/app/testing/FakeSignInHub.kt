package com.homelab.household.app.testing

import com.homelab.household.domain.model.Member
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
 * The hub as signing in needs it, shared by "Who's here?" and the PIN pad:
 * `GET /api/v1/auth/members` and `POST /api/v1/auth/login`, keeping every sign-in body it was sent.
 */
class FakeSignInHub {

    private val sent = mutableListOf<String>()
    val signIns: List<String> get() = sent.toList()

    private var members: suspend MockRequestHandleScope.() -> HttpResponseData = { notSaid() }
    private var login: suspend MockRequestHandleScope.() -> HttpResponseData = { notSaid() }

    val engine: HttpClientEngine = MockEngine { request ->
        when (request.url.encodedPath) {
            "/api/v1/auth/members" -> members()
            "/api/v1/auth/login" -> {
                sent += (request.body as TextContent).text
                login()
            }
            else -> respond("", HttpStatusCode.NotFound)
        }
    }

    fun lists(vararg people: Member) {
        val body = people.joinToString(prefix = "[", postfix = "]") {
            """{"id":"${it.id}","full_name":"${it.name}","avatar_color":"${it.avatarColor}"}"""
        }
        members = { json(body) }
    }

    fun acceptsThePinOf(member: Member) {
        login = {
            json(
                """{"access_token":"signed-in-token","token_type":"bearer","user":{"id":"${member.id}",
                    "full_name":"${member.name}","avatar_color":"${member.avatarColor}","is_admin":false,
                    "is_active":true,"created_at":"2026-09-13T00:00:00Z"}}"""
            )
        }
    }

    fun refusesThePin(attemptsLeft: Int) {
        login = { json("""{"detail":"Wrong PIN","attempts_left":$attemptsLeft}""", HttpStatusCode.Unauthorized) }
    }

    fun locksFor(seconds: Int) {
        login = { json("""{"detail":"Too many wrong PINs","retry_after_seconds":$seconds}""", HttpStatusCode.TooManyRequests) }
    }

    /** 503: a proxy in front of a hub that isn't running. */
    fun isOffline() {
        members = { respond("", HttpStatusCode.ServiceUnavailable) }
        login = { respond("", HttpStatusCode.ServiceUnavailable) }
    }

    private fun MockRequestHandleScope.notSaid() =
        respond("The test did not say what the hub should answer", HttpStatusCode.NotImplemented)

    private fun MockRequestHandleScope.json(content: String, status: HttpStatusCode = HttpStatusCode.OK) = respond(
        content = content,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    )
}
