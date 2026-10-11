package com.homelab.household.data.network

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.encodedPath
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException

/**
 * The tag failed hub calls are logged under. Deliberately not [HTTP_CLIENT_LOG_TAG]: lines under
 * that one never leave the phone, and these are meant to.
 */
const val REQUEST_LOG_TAG = "Network"

/**
 * Logs every hub call that did not succeed, at warn, as one line: the method, the path with every
 * id taken out (see [templateRequestPath]) and either the status or the kind of failure.
 *
 * The line is exported, so what it leaves out matters more than what it says. Never the host, the
 * query, a header or a body — and for a call that threw, only the exception's class name, because
 * its message is where an engine puts the address it could not reach. A call that was cancelled is
 * somebody leaving a screen, not a failure, and is not logged.
 */
fun HttpClientConfig<*>.logFailedRequests(log: Logger = Logger.withTag(REQUEST_LOG_TAG)) {
    val plugin =
        createClientPlugin("RequestFailureLog") {
            on(Send) { request ->
                val method = request.method.value
                val path = templateRequestPath(request.url.encodedPath)
                val call =
                    try {
                        proceed(request)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (cause: Throwable) {
                        log.w { "Request failed: $method $path -> ${cause::class.simpleName ?: "Unknown"}" }
                        throw cause
                    }
                val status = call.response.status
                if (!status.isSuccess()) {
                    log.w { "Request failed: $method $path -> ${status.value}" }
                }
                call
            }
        }
    install(plugin)
}

/**
 * A hub path with everything that names a person, a conversation or a code replaced by `{id}`, and
 * nothing after the path: `/api/v1/sessions/3f2a…/tools/call_x9/decision` becomes
 * `/api/v1/sessions/{id}/tools/{id}/decision`.
 *
 * It keeps the words it knows rather than removing the shapes it recognises. A UUID or a `call_…`
 * id is easy to spot, but an invite code or a PIN-reset code is a credential made of plain words,
 * and a member id can be a name. So a segment survives only if it is one of [fixedSegments]; a new
 * endpoint shows as `{id}` until its words are added, which `RequestPathWordsTest` insists on.
 */
fun templateRequestPath(path: String): String =
    path
        .substringBefore('?')
        .substringBefore('#')
        .let { if ("://" in it) "/" + it.substringAfter("://").substringAfter('/', "") else it }
        .split('/')
        .joinToString("/") { segment -> if (segment.isEmpty() || segment in fixedSegments) segment else "{id}" }

private val fixedSegments =
    setOf(
        "api",
        "v1",
        "agents",
        "archive",
        "audit",
        "auth",
        "calendars",
        "chat",
        "decision",
        "google",
        "gossip",
        "health",
        "household",
        "integrations",
        "invites",
        "login",
        "me",
        "members",
        "memories",
        "personal",
        "pin",
        "pin-resets",
        "redeem",
        "refresh",
        "regenerate",
        "register-initial",
        "restore",
        "secret",
        "sessions",
        "settings",
        "shared",
        "spaces",
        "start",
        "status",
        "stream",
        "tool-approvals",
        "tools",
        "users",
    )
