package com.homelab.household.app.testing

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
 * The hub as joining needs it: looking up an invite code, redeeming it, and the member list the
 * join screen reads to see which colours are taken. Every redeem body it was sent is kept.
 */
class FakeJoinHub {

    private val sent = mutableListOf<String>()
    val joins: List<String> get() = sent.toList()

    private var lookUp: suspend MockRequestHandleScope.() -> HttpResponseData = { notSaid() }
    private var redeem: suspend MockRequestHandleScope.() -> HttpResponseData = { notSaid() }
    private var members: suspend MockRequestHandleScope.() -> HttpResponseData = { json("[]") }

    val engine: HttpClientEngine = MockEngine { request ->
        val path = request.url.encodedPath
        when {
            path == "/api/v1/auth/members" -> members()
            path.endsWith("/redeem") -> {
                sent += (request.body as TextContent).text
                redeem()
            }
            path.startsWith("/api/v1/invites/") -> lookUp()
            else -> respond("", HttpStatusCode.NotFound)
        }
    }

    fun knowsTheCodeOf(invitedName: String, inviterName: String, inviterColour: String = "#3C6E4E") {
        lookUp = {
            json(
                """{"invited_name":"$invitedName","inviter_name":"$inviterName",
                    "inviter_avatar_color":"$inviterColour"}"""
            )
        }
    }

    fun knowsNoSuchCode() {
        lookUp = { json("""{"detail":"That invite code isn't valid.","code":"invite_invalid"}""", HttpStatusCode.BadRequest) }
    }

    fun hasHadEnoughGuessing(seconds: Int) {
        val body = """{"detail":"Too many wrong codes.","code":"code_guesses_locked","retry_after_seconds":$seconds}"""
        lookUp = { json(body, HttpStatusCode.TooManyRequests) }
        redeem = { json(body, HttpStatusCode.TooManyRequests) }
    }

    fun letsThemJoinAs(memberId: String, fullName: String, colour: String = "#C05638") {
        redeem = {
            json(
                """{"access_token":"joined-token","token_type":"bearer","user":{"id":"$memberId",
                    "full_name":"$fullName","avatar_color":"$colour","is_admin":false,"is_active":true,
                    "created_at":"2026-09-16T00:00:00Z"}}""",
                HttpStatusCode.Created
            )
        }
    }

    fun saysThatNameIsTaken() {
        redeem = {
            json(
                """{"detail":"Someone in the household already has that name.","code":"name_taken"}""",
                HttpStatusCode.Conflict
            )
        }
    }

    fun saysTheCodeHasGone() {
        redeem = { json("""{"detail":"That invite code isn't valid.","code":"invite_invalid"}""", HttpStatusCode.BadRequest) }
    }

    /** Who already lives here, so the join screen can grey out the colours they wear. */
    fun listsMembers(vararg colours: String) {
        val body = colours.mapIndexed { index, colour ->
            """{"id":"m$index","full_name":"Member $index","avatar_color":"$colour"}"""
        }.joinToString(prefix = "[", postfix = "]")
        members = { json(body) }
    }

    /** 503: a proxy in front of a hub that isn't running. */
    fun isOffline() {
        lookUp = { respond("", HttpStatusCode.ServiceUnavailable) }
        redeem = { respond("", HttpStatusCode.ServiceUnavailable) }
        members = { respond("", HttpStatusCode.ServiceUnavailable) }
    }

    private fun MockRequestHandleScope.notSaid() =
        respond("The test did not say what the hub should answer", HttpStatusCode.NotImplemented)

    private fun MockRequestHandleScope.json(content: String, status: HttpStatusCode = HttpStatusCode.OK) = respond(
        content = content,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    )
}
