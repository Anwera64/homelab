package com.homelab.household.data.telemetry

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import com.homelab.household.data.network.HTTP_CLIENT_LOG_TAG
import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.logging.Logger
import io.opentelemetry.kotlin.logging.SeverityNumber

/** The record attribute a Kermit tag is kept in. */
const val LOG_TAG_ATTRIBUTE = "log.tag"

/**
 * Turns a Kermit log line into an OpenTelemetry log record, and decides which lines leave the phone.
 *
 * Two rules, both about what is refused. Nothing below info is sent: debug and verbose are for the
 * person holding the phone and a cable. And nothing under [HTTP_CLIENT_LOG_TAG] is sent at any
 * severity, because those are Ktor's own lines and in a debug build they carry request bodies and
 * the Authorization header.
 *
 * A failure logged with the line gives its class and its message and nothing else. It is not handed
 * to the SDK as an exception, because the SDK would attach the stack trace.
 */
@OptIn(ExperimentalApi::class)
class OtelLogWriter(
    private val logger: Logger,
) : LogWriter() {
    override fun isLoggable(
        tag: String,
        severity: Severity,
    ): Boolean = severity >= Severity.Info && tag != HTTP_CLIENT_LOG_TAG

    override fun log(
        severity: Severity,
        message: String,
        tag: String,
        throwable: Throwable?,
    ) {
        if (!isLoggable(tag, severity)) return
        logger.emit(
            body = message,
            severityNumber = severity.toSeverityNumber(),
            severityText = severity.name.uppercase(),
        ) {
            setStringAttribute(LOG_TAG_ATTRIBUTE, tag)
            if (throwable != null) {
                setStringAttribute("exception.type", throwable::class.simpleName ?: "Unknown")
            }
        }
    }

    private fun Severity.toSeverityNumber(): SeverityNumber =
        when (this) {
            Severity.Verbose -> SeverityNumber.TRACE
            Severity.Debug -> SeverityNumber.DEBUG
            Severity.Info -> SeverityNumber.INFO
            Severity.Warn -> SeverityNumber.WARN
            Severity.Error -> SeverityNumber.ERROR
            Severity.Assert -> SeverityNumber.FATAL
        }
}
