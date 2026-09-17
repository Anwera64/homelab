package com.homelab.household.data.remote

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger as KermitLogger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.loggerConfigInit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KermitKtorLoggerTest {

    private class RecordingLogWriter : LogWriter() {
        val records = mutableListOf<LogRecord>()

        data class LogRecord(
            val severity: Severity,
            val message: String,
            val tag: String,
            val throwable: Throwable?
        )

        override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
            records.add(LogRecord(severity, message, tag, throwable))
        }
    }

    @Test
    fun logs_message_to_kermit_with_default_httpclient_tag() {
        val writer = RecordingLogWriter()
        val testKermit = KermitLogger(config = loggerConfigInit(writer))
        val ktorLogger = KermitKtorLogger(kermit = testKermit)

        ktorLogger.log("GET /api/v1/health -> 200 OK")

        assertEquals(1, writer.records.size)
        val record = writer.records.first()
        assertEquals(Severity.Debug, record.severity)
        assertEquals("HttpClient", record.tag)
        assertEquals("GET /api/v1/health -> 200 OK", record.message)
    }

    @Test
    fun logs_message_with_custom_tag() {
        val writer = RecordingLogWriter()
        val testKermit = KermitLogger(config = loggerConfigInit(writer))
        val ktorLogger = KermitKtorLogger(tag = "CustomNetwork", kermit = testKermit)

        ktorLogger.log("POST /api/v1/login")

        assertEquals(1, writer.records.size)
        val record = writer.records.first()
        assertEquals("CustomNetwork", record.tag)
        assertEquals("POST /api/v1/login", record.message)
    }
}
