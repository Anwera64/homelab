package com.homelab.household.data.network

import io.ktor.client.plugins.logging.Logger
import co.touchlab.kermit.Logger as KermitLogger

/**
 * The tag Ktor's own log lines go under. In a debug build those lines carry request bodies and the
 * Authorization header, so the telemetry writer refuses the tag outright, whatever the severity.
 */
const val HTTP_CLIENT_LOG_TAG = "HttpClient"

/**
 * Ktor [Logger] adapter that delegates HTTP network log messages to [co.touchlab.kermit.Logger].
 *
 * This ensures that on Android logs flow into Logcat with proper tags, on iOS logs flow into
 * Apple's Unified Logging system (os_log), and on desktop/tests logs are routed to stdout.
 */
class KermitKtorLogger(
    private val tag: String = HTTP_CLIENT_LOG_TAG,
    private val kermit: KermitLogger = KermitLogger,
) : Logger {
    override fun log(message: String) {
        kermit.d(tag = tag) { message }
    }
}
