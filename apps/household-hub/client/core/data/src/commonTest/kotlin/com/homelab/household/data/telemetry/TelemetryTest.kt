package com.homelab.household.data.telemetry

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.loggerConfigInit
import com.homelab.household.data.datasource.local.InMemorySessionStorage
import com.homelab.household.data.dto.UserReadDto
import io.opentelemetry.kotlin.ExperimentalApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalApi::class)
class TelemetryTest {
    private val device = mapOf("os.name" to "TestOS", "os.version" to "1.2", "device.model.identifier" to "Bench 3000")

    // ---- the headers every export carries ----------------------------------

    @Test
    fun `GIVEN a signed-in phone WHEN the export headers are asked for THEN they carry its token as a bearer`() {
        val storage = InMemorySessionStorage().apply { saveTokens("jwt-token-123") }

        val headers = telemetryHeaders(storage)

        assertEquals(mapOf("Authorization" to "Bearer jwt-token-123"), headers)
    }

    @Test
    fun `GIVEN nobody signed in WHEN the export headers are asked for THEN there are none`() {
        val storage = InMemorySessionStorage()

        val headers = telemetryHeaders(storage)

        assertTrue(headers.isEmpty())
    }

    @Test
    fun `GIVEN a signed-in member WHEN the export headers are asked for THEN nothing says who they are`() {
        val storage =
            InMemorySessionStorage().apply {
                saveTokens("jwt-token-123")
                saveUser(
                    UserReadDto(
                        id = "emma",
                        full_name = "Emma",
                        is_admin = true,
                        is_active = true,
                        personal_space_id = "sp-1",
                        avatar_color = "#3C6E4E",
                        created_at = "2026-09-13T00:00:00Z",
                    ),
                )
            }

        val headers = telemetryHeaders(storage)

        assertEquals(setOf("Authorization"), headers.keys)
    }

    // ---- switching it off ---------------------------------------------------

    @Test
    fun `GIVEN an empty endpoint WHEN telemetry is created THEN there is no writer and the SDK is never started`() {
        var started = false

        val writer =
            createTelemetryLogWriter(TelemetryConfig(endpoint = ""), device) {
                started = true
                RecordingLogRecordProcessor()
            }

        assertNull(writer)
        assertFalse(started)
        assertFalse(TelemetryConfig(endpoint = "  ").isEnabled)
        assertFalse(TelemetryConfig.Disabled.isEnabled)
    }

    @Test
    fun `GIVEN an endpoint written with a trailing slash WHEN it is read THEN the slash is gone`() {
        val config = TelemetryConfig(endpoint = "https://telemetry.test/")

        val endpoint = config.endpoint

        assertEquals("https://telemetry.test", endpoint)
    }

    // ---- what every record says about where it came from --------------------

    @Test
    fun `GIVEN an endpoint and an app version WHEN a line is logged THEN the record says which service app and device it came from`() {
        val processor = RecordingLogRecordProcessor()
        val writer =
            createTelemetryLogWriter(
                TelemetryConfig("https://telemetry.test", appVersion = "0.1"),
                device,
            ) { processor }
        val log = Logger(config = loggerConfigInit(requireNotNull(writer)), tag = "App")

        log.i { "App started" }

        val resource =
            processor.records
                .single()
                .resource.attributes
        assertEquals("household-hub-app", resource["service.name"])
        assertEquals("0.1", resource["app.version"])
        assertEquals("TestOS", resource["os.name"])
        assertEquals("1.2", resource["os.version"])
        assertEquals("Bench 3000", resource["device.model.identifier"])
    }

    @Test
    fun `GIVEN this machine WHEN its device attributes are read THEN they name an operating system`() {
        val key = "os.name"

        val attributes = deviceAttributes()

        assertFalse(attributes[key].isNullOrBlank())
    }

    // ---- starting -----------------------------------------------------------

    @Test
    fun `GIVEN telemetry with a writer WHEN it starts THEN the writer is installed and the start is logged once`() {
        val processor = RecordingLogRecordProcessor()
        val writer =
            requireNotNull(createTelemetryLogWriter(TelemetryConfig("https://telemetry.test"), device) { processor })
        val telemetry = Telemetry(writer)
        val installed = mutableListOf<LogWriter>()
        val console = RecordingLogWriter()

        telemetry.start(install = { installed += it }, log = Logger(loggerConfigInit(console, writer), tag = "App"))

        assertTrue(telemetry.isEnabled)
        assertSame(writer, installed.single())
        assertEquals(listOf(RecordingLogWriter.Line(Severity.Info, "App", "App started")), console.lines)
        assertEquals(listOf<Any?>("App started"), processor.records.map { it.body })
    }

    @Test
    fun `GIVEN telemetry switched off WHEN it starts THEN nothing is installed and the start is still logged on the phone`() {
        val telemetry = Telemetry(logWriter = null)
        val installed = mutableListOf<LogWriter>()
        val console = RecordingLogWriter()

        telemetry.start(install = { installed += it }, log = Logger(loggerConfigInit(console), tag = "App"))

        assertFalse(telemetry.isEnabled)
        assertTrue(installed.isEmpty())
        assertEquals(listOf(RecordingLogWriter.Line(Severity.Info, "App", "App started")), console.lines)
    }

    @Test
    fun `GIVEN telemetry already started WHEN it is started again THEN the writer is not installed twice and the start is not logged twice`() {
        val writer =
            requireNotNull(
                createTelemetryLogWriter(TelemetryConfig("https://telemetry.test"), device) {
                    RecordingLogRecordProcessor()
                },
            )
        val telemetry = Telemetry(writer)
        val installed = mutableListOf<LogWriter>()
        val console = RecordingLogWriter()
        telemetry.start(install = { installed += it }, log = console.logger("App"))

        telemetry.start(install = { installed += it }, log = console.logger("App"))

        assertTrue(telemetry.isStarted)
        assertEquals(1, installed.size)
        assertEquals(1, console.lines.size)
    }
}
