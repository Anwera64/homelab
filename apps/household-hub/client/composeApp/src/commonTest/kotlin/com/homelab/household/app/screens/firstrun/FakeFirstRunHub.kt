package com.homelab.household.app.screens.firstrun

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
 * The hub as first run needs it: `POST /api/v1/auth/register-initial`, keeping every body it was
 * sent so a test can read what the form posted.
 */
class FakeFirstRunHub {
    private val sent = mutableListOf<String>()
    val registrations: List<String> get() = sent.toList()

    private var answer: suspend MockRequestHandleScope.() -> HttpResponseData = {
        respond("The test did not say what the hub should answer", HttpStatusCode.NotImplemented)
    }

    val engine: HttpClientEngine =
        MockEngine { request ->
            if (request.url.encodedPath == "/api/v1/auth/register-initial") {
                sent += (request.body as TextContent).text
                answer()
            } else {
                respond("", HttpStatusCode.NotFound)
            }
        }

    fun createsTheHousehold() {
        answer = {
            respond(
                content = """{"access_token":"first-token","token_type":"bearer","user":{"id":"emma",
                    "full_name":"Emma","avatar_color":"#C05638","is_admin":true,"is_active":true,
                    "personal_space_id":"space-1","created_at":"2026-09-13T00:00:00Z"}}""",
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
    }

    /** 503: a proxy in front of a hub that isn't running. */
    fun isOffline() {
        answer = { respond("", HttpStatusCode.ServiceUnavailable) }
    }

    /** Someone set the hub up between launch checking and this form being sent. */
    fun isAlreadySetUp() {
        answer = {
            respond(
                content = """{"detail":"System is already initialized."}""",
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
    }
}
