package com.homelab.household.data.di

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.loggerConfigInit
import co.touchlab.kermit.platformLogWriter
import com.homelab.household.data.datasource.local.AuthEventsLocalDataSource
import com.homelab.household.data.datasource.local.InMemorySessionStorage
import com.homelab.household.data.datasource.local.StoredSessionLocalDataSource
import com.homelab.household.data.network.HubConfig
import com.homelab.household.data.network.REQUEST_LOG_TAG
import com.homelab.household.data.network.TIME_ZONE_HEADER
import com.homelab.household.data.telemetry.Telemetry
import com.homelab.household.data.telemetry.TelemetryConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.io.ByteArrayInputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.zip.GZIPInputStream

/**
 * The real graph, the real SDK and the real OTLP exporter, with only the engine swapped for one
 * that answers from memory. This is where the pieces are proven to be joined up the way the app
 * needs — and it is on the JVM, with a real clock, because the SDK batches on its own dispatcher.
 */
class TelemetryWiringTest {
    private class Seen(
        val host: String,
        val path: String,
        val authorization: String?,
        val timeZone: String?,
        val contentType: String?,
        val body: ByteArray,
    )

    private val seen = CopyOnWriteArrayList<Seen>()
    private val started = mutableListOf<KoinApplication>()

    /** The log endpoint refuses everything with a 401; the hub answers every call with a 503. */
    private val engine =
        MockEngine { request ->
            seen +=
                Seen(
                    host = request.url.host,
                    path = request.url.encodedPath,
                    authorization = request.headers[HttpHeaders.Authorization],
                    timeZone = request.headers[TIME_ZONE_HEADER],
                    contentType = request.body.contentType?.toString(),
                    body = request.body.toByteArray(),
                )
            if (request.url.host == "telemetry.test") {
                respond("", HttpStatusCode.Unauthorized)
            } else {
                respond("{}", HttpStatusCode.ServiceUnavailable)
            }
        }

    private fun graph(
        telemetry: TelemetryConfig,
        storage: StoredSessionLocalDataSource = InMemorySessionStorage(),
    ): Koin =
        koinApplication {
            modules(
                dataModule,
                module {
                    single { HubConfig(baseUrl = "https://hub.test") }
                    single { telemetry }
                    single<HttpClientEngine> { engine }
                    single { storage }
                },
            )
        }.also { started += it }.koin

    /**
     * `dataModule` is one object and a Koin `single` keeps its instance in the module, so a graph
     * left open would hand the next test this one's Telemetry. Closing it is what lets go.
     */
    @AfterEach
    fun closeGraphs() {
        started.forEach { it.close() }
    }

    private fun await(
        what: String,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + 20_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Timed out waiting for $what" }
            Thread.sleep(25)
        }
    }

    private fun Seen.unzipped(): String =
        GZIPInputStream(ByteArrayInputStream(body)).use { String(it.readBytes(), Charsets.ISO_8859_1) }

    private fun exported(): String = seen.filter { it.host == "telemetry.test" }.joinToString("\n") { it.unzipped() }

    /** Installs [Telemetry]'s writer on a logger of the test's own rather than on the global Kermit. */
    private fun Koin.startedLogger(tag: String): Logger {
        var writer: LogWriter? = null
        get<Telemetry>().start(install = { writer = it }, log = Logger(loggerConfigInit(), tag = "App"))
        return Logger(loggerConfigInit(requireNotNull(writer) { "telemetry installed no writer" }), tag = tag)
    }

    @Test
    fun `GIVEN no telemetry endpoint WHEN the graph builds telemetry THEN it is off and nothing is sent`() {
        // GIVEN
        val koin = graph(TelemetryConfig.Disabled)

        // WHEN
        val telemetry = koin.get<Telemetry>()

        // THEN
        assertFalse(telemetry.isEnabled)
        assertTrue(seen.isEmpty())
    }

    @Test
    fun `GIVEN a signed-in phone and an endpoint that answers 401 WHEN a line is logged THEN it is posted with the token and nobody is signed out`() {
        // GIVEN
        val storage = InMemorySessionStorage().apply { saveTokens("jwt-token-123") }
        val koin = graph(TelemetryConfig("https://telemetry.test/", appVersion = "0.1"), storage)
        val signedOut = CopyOnWriteArrayList<Unit>()
        val watching = CoroutineScope(Dispatchers.Default)
        koin
            .get<AuthEventsLocalDataSource>()
            .observeSignedOut()
            .onEach { signedOut += it }
            .launchIn(watching)
        val log = koin.startedLogger("Auth")

        // WHEN
        log.i { "Signed in" }

        // THEN
        await("the export") { seen.isNotEmpty() }
        Thread.sleep(500) // long enough for the 401 to be read and acted on, were anything to act on it
        val post = seen.single()
        assertEquals("telemetry.test", post.host)
        assertEquals("/v1/logs", post.path)
        assertEquals("Bearer jwt-token-123", post.authorization)
        assertEquals("application/x-protobuf", post.contentType)
        assertNull(post.timeZone, "the hub client's plugins ran, so this went through the hub's client")
        val sent = post.unzipped()
        assertTrue("household-hub-app" in sent, "service.name missing from: $sent")
        assertTrue("Signed in" in sent)
        assertFalse("jwt-token-123" in sent, "the token is a header, never part of a record")
        assertEquals("jwt-token-123", storage.getAccessToken(), "a 401 from the log endpoint signed the member out")
        assertTrue(signedOut.isEmpty())
        watching.cancel()
    }

    @Test
    fun `GIVEN nobody signed in WHEN a line is logged THEN it is posted without an Authorization header`() {
        // GIVEN
        val koin = graph(TelemetryConfig("https://telemetry.test"))
        val log = koin.startedLogger("App")

        // WHEN
        log.w { "Something worth a warning" }

        // THEN
        await("the export") { seen.isNotEmpty() }
        assertNull(seen.first().authorization)
    }

    @Test
    fun `GIVEN the app's own client WHEN a hub call fails THEN it is logged as a failed request and the log endpoint's refusal is not`() {
        // GIVEN
        val lines = CopyOnWriteArrayList<Triple<Severity, String, String>>()
        val recorder =
            object : LogWriter() {
                override fun log(
                    severity: Severity,
                    message: String,
                    tag: String,
                    throwable: Throwable?,
                ) {
                    lines += Triple(severity, tag, message)
                }
            }
        val koin =
            graph(TelemetryConfig("https://telemetry.test"), InMemorySessionStorage().apply { saveTokens("jwt") })
        Logger.setLogWriters(recorder)
        try {
            koin.get<Telemetry>().start()

            // WHEN
            runBlocking {
                koin.get<HttpClient>().get(
                    "https://hub.test/api/v1/sessions/3f2a9c1e-7b64-4d0a-9e51-0c8d2b6f4a17?q=x",
                )
            }

            // THEN
            val failure = "Request failed: GET /api/v1/sessions/{id} -> 503"
            await("both lines to be exported") { exported().let { "App started" in it && failure in it } }
            Thread.sleep(500) // room for a refused export to be logged, were it going to be
            val failures = lines.filter { it.second == REQUEST_LOG_TAG }
            assertEquals(
                listOf(Triple(Severity.Warn, REQUEST_LOG_TAG, failure)),
                failures,
                "exactly one failed request: the hub's, and not the log endpoint's own 401",
            )
            assertFalse("3f2a9c1e" in exported())
        } finally {
            Logger.setLogWriters(platformLogWriter())
        }
    }
}
