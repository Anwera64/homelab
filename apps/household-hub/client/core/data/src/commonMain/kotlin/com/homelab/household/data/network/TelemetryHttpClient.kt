package com.homelab.household.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.compression.ContentEncodingConfig

private const val EXPORT_TIMEOUT_MS = 10_000L

/**
 * The client the app's logs are posted with — never the one it talks to the hub with.
 *
 * The hub's client signs the member out on a 401 (`signOutOnUnauthorized`), and a 401 is exactly
 * what the log endpoint answers once a token has expired; sending logs through it would turn a
 * dropped batch into a lost session. So this one is built from the same engine and nothing else: no
 * sign-out, no Ktor logging (its own requests must not become log lines to send), no failure log.
 *
 * [headers] is asked on every post rather than once, for the reason `installBearerAuth` reads its
 * token each time: whoever is signed in can change while the app runs. The engine's own timeouts
 * are set for a streamed answer, which is to say there are none, hence the one here; and the SDK
 * gzips what it sends, which is what [ContentEncoding] is installed for.
 */
fun telemetryHttpClient(
    engine: HttpClientEngine,
    headers: suspend () -> Map<String, String>,
): HttpClient =
    HttpClient(engine) {
        install(HttpTimeout) {
            requestTimeoutMillis = EXPORT_TIMEOUT_MS
        }
        install(ContentEncoding) {
            mode = ContentEncodingConfig.Mode.All
            gzip()
        }
        install(
            createClientPlugin("TelemetryHeaders") {
                onRequest { request, _ ->
                    headers().forEach { (name, value) -> request.headers[name] = value }
                }
            },
        )
    }
