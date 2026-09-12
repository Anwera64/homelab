package com.homelab.household.app.screens.launch

import com.homelab.household.presentation.launch.HubStatus
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.awaitCancellation

/**
 * The hub as the launch screen needs it: `GET /api/v1/auth/status` and nothing else.
 *
 * One engine with a swappable answer, so a test can change the hub's mind halfway through —
 * which "Try again" needs. When a second screen needs a hub, lift the shared parts out of here
 * into a common fake rather than growing this one.
 */
class FakeLaunchHub {

    private var answer: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = {
        respond("The test did not say what the hub should answer", HttpStatusCode.NotImplemented)
    }

    val engine: HttpClientEngine = MockEngine { request -> answer(request) }

    fun respondsWith(initialized: Boolean, members: Int) {
        answer = {
            respond(
                content = """{"is_initialized":$initialized,"member_count":$members}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
    }

    /** 503: `AuthRepositoryImpl.checkStatus` reads 502..504 as the hub being down. */
    fun isOffline() {
        answer = { respond("", HttpStatusCode.ServiceUnavailable) }
    }

    /** A captive portal or a proxy error page: it answers, just not with JSON. */
    fun answersWithHtml() {
        answer = {
            respond(
                content = "<html><body>Sign in to the network</body></html>",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString())
            )
        }
    }

    fun fails(status: HttpStatusCode) {
        answer = { respond("", status) }
    }

    /** Leaves the screen waiting, which is what `Checking` looks like. */
    fun neverAnswers() {
        answer = { awaitCancellation() }
    }

    /** The hub behaviour that produces a given status, for walking every previewed state. */
    fun producing(status: HubStatus) = when (status) {
        HubStatus.Checking -> neverAnswers()
        is HubStatus.Ready -> respondsWith(initialized = true, members = status.memberCount)
        HubStatus.FirstRun -> respondsWith(initialized = false, members = 0)
        HubStatus.Unreachable -> isOffline()
        is HubStatus.Failed -> fails(HttpStatusCode.InternalServerError)
    }
}
