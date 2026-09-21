package com.homelab.household.data.network

import co.touchlab.kermit.Logger as KermitLogger
import io.ktor.client.plugins.logging.Logger

/**
 * Ktor [Logger] adapter that delegates HTTP network log messages to [co.touchlab.kermit.Logger].
 *
 * This ensures that on Android logs flow into Logcat with proper tags, on iOS logs flow into
 * Apple's Unified Logging system (os_log), and on desktop/tests logs are routed to stdout.
 */
class KermitKtorLogger(
    private val tag: String = "HttpClient",
    private val kermit: KermitLogger = KermitLogger
) : Logger {

    override fun log(message: String) {
        kermit.d(tag = tag) { message }
    }
}
