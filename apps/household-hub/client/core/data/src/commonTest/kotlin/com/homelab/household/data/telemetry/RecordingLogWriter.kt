package com.homelab.household.data.telemetry

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.loggerConfigInit

/** Keeps every Kermit line it is handed, so a test can say what was logged and at what severity. */
class RecordingLogWriter : LogWriter() {
    data class Line(
        val severity: Severity,
        val tag: String,
        val message: String,
        val throwable: Throwable? = null,
    )

    val lines = mutableListOf<Line>()

    override fun log(
        severity: Severity,
        message: String,
        tag: String,
        throwable: Throwable?,
    ) {
        lines += Line(severity, tag, message, throwable)
    }

    /** A logger that writes here and nowhere else — not to the console, not to the global Kermit. */
    fun logger(tag: String): Logger = Logger(config = loggerConfigInit(this), tag = tag)
}
