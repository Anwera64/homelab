package com.homelab.household.data.network

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import co.touchlab.kermit.loggerConfigInit
import kotlin.test.Test
import kotlin.test.assertEquals
import co.touchlab.kermit.Logger as KermitLogger

class KermitKtorLoggerTest {
    private class RecordingLogWriter : LogWriter() {
        val records = mutableListOf<LogRecord>()

        data class LogRecord(
            val severity: Severity,
            val message: String,
            val tag: String,
            val throwable: Throwable?,
        )

        override fun log(
            severity: Severity,
            message: String,
            tag: String,
            throwable: Throwable?,
        ) {
            records.add(LogRecord(severity, message, tag, throwable))
        }
    }

    @Test
    fun `GIVEN a logger with no tag of its own WHEN Ktor logs a line THEN Kermit records it as debug under HttpClient`() {
        // GIVEN
        val writer = RecordingLogWriter()
        val ktorLogger = KermitKtorLogger(kermit = KermitLogger(config = loggerConfigInit(writer)))

        // WHEN
        ktorLogger.log("GET /api/v1/health -> 200 OK")

        // THEN
        assertEquals(1, writer.records.size)
        val record = writer.records.first()
        assertEquals(Severity.Debug, record.severity)
        assertEquals("HttpClient", record.tag)
        assertEquals("GET /api/v1/health -> 200 OK", record.message)
    }

    @Test
    fun `GIVEN a logger given its own tag WHEN Ktor logs a line THEN Kermit records it under that tag`() {
        // GIVEN
        val writer = RecordingLogWriter()
        val ktorLogger =
            KermitKtorLogger(
                tag = "CustomNetwork",
                kermit = KermitLogger(config = loggerConfigInit(writer)),
            )

        // WHEN
        ktorLogger.log("POST /api/v1/login")

        // THEN
        assertEquals(1, writer.records.size)
        val record = writer.records.first()
        assertEquals("CustomNetwork", record.tag)
        assertEquals("POST /api/v1/login", record.message)
    }
}
