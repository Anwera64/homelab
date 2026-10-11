package com.homelab.household.data.telemetry

import co.touchlab.kermit.Logger
import co.touchlab.kermit.loggerConfigInit
import com.homelab.household.data.network.KermitKtorLogger
import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.createOpenTelemetry
import io.opentelemetry.kotlin.logging.SeverityNumber
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [OtelLogWriter] decides which of the app's log lines leave the phone. The lines it must refuse
 * are Ktor's own, which in a debug build carry request bodies and the Authorization header.
 */
@OptIn(ExperimentalApi::class)
class OtelLogWriterTest {
    private val processor = RecordingLogRecordProcessor()

    private val kermit: Logger =
        Logger(
            config =
                loggerConfigInit(
                    OtelLogWriter(
                        createOpenTelemetry { loggerProvider { export { processor } } }
                            .loggerProvider
                            .getLogger("test"),
                    ),
                ),
        )

    @Test
    fun `GIVEN the writer installed WHEN a line is logged at info THEN a record carries its words its tag and its severity`() {
        // GIVEN
        val log = kermit.withTag("Auth")

        // WHEN
        log.i { "Signed in" }

        // THEN
        val record = processor.records.single()
        assertEquals("Signed in", record.body)
        assertEquals(SeverityNumber.INFO, record.severityNumber)
        assertEquals("INFO", record.severityText)
        assertEquals("Auth", record.attributes[LOG_TAG_ATTRIBUTE])
    }

    @Test
    fun `GIVEN the writer installed WHEN a line is logged at warn THEN a record is made at warn`() {
        // GIVEN
        val log = kermit.withTag("Network")

        // WHEN
        log.w { "Request failed" }

        // THEN
        assertEquals(SeverityNumber.WARN, processor.records.single().severityNumber)
    }

    @Test
    fun `GIVEN the writer installed WHEN a line is logged at error THEN a record is made at error`() {
        // GIVEN
        val log = kermit.withTag("Chat")

        // WHEN
        log.e { "Something broke" }

        // THEN
        assertEquals(SeverityNumber.ERROR, processor.records.single().severityNumber)
    }

    @Test
    fun `GIVEN the writer installed WHEN a line is logged at debug THEN no record is made`() {
        // GIVEN
        val log = kermit.withTag("Auth")

        // WHEN
        log.d { "token read from storage" }

        // THEN
        assertTrue(processor.records.isEmpty())
    }

    @Test
    fun `GIVEN the writer installed WHEN a line is logged at verbose THEN no record is made`() {
        // GIVEN
        val log = kermit.withTag("Auth")

        // WHEN
        log.v { "token read from storage" }

        // THEN
        assertTrue(processor.records.isEmpty())
    }

    @Test
    fun `GIVEN a line under Ktor's tag WHEN it is logged at error THEN no record is made`() {
        // GIVEN
        val log = kermit.withTag("HttpClient")

        // WHEN
        log.e { "REQUEST https://hub.test/api/v1/auth/login failed" }

        // THEN
        assertTrue(processor.records.isEmpty())
    }

    @Test
    fun `GIVEN Ktor logging a request with its Authorization header and body WHEN the lines reach the writer THEN nothing of them is exported`() {
        // GIVEN
        val ktorLogger = KermitKtorLogger(kermit = kermit)

        // WHEN
        ktorLogger.log(
            "REQUEST: https://hub.test/api/v1/auth/login\n" +
                "-> Authorization: Bearer jwt-token-123\n" +
                "BODY START\n{\"pin\":\"482913\"}\nBODY END",
        )
        kermit.withTag("HttpClient").e { "-> Authorization: Bearer jwt-token-123 {\"pin\":\"482913\"}" }
        kermit.withTag("App").i { "App started" }

        // THEN
        val exported = processor.records.joinToString("\n") { it.everythingExported() }
        assertEquals(listOf<Any?>("App started"), processor.records.map { it.body })
        listOf("Authorization", "Bearer", "jwt-token-123", "482913", "pin").forEach {
            assertFalse(it in exported, "'$it' was exported: $exported")
        }
    }

    @Test
    fun `GIVEN a line logged with a failure WHEN it becomes a record THEN it names the failure's kind and words but not its stack`() {
        // GIVEN
        val log = kermit.withTag("Chat")
        val failure = IllegalStateException("the stream closed early")

        // WHEN
        log.e(failure) { "Chat stream dropped" }

        // THEN
        val record = processor.records.single()
        assertEquals("Chat stream dropped", record.body)
        assertEquals("IllegalStateException", record.attributes["exception.type"])
        // A message can carry an address or a file path, so only the type leaves the device.
        assertFalse("exception.message" in record.attributes.keys)
        assertFalse("exception.stacktrace" in record.attributes.keys)
        assertFalse("OtelLogWriterTest" in record.everythingExported(), "a stack frame was exported")
    }
}
