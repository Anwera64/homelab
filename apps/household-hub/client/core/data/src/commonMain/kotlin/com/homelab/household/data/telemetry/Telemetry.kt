package com.homelab.household.data.telemetry

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import com.homelab.household.data.datasource.local.StoredSessionLocalDataSource
import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.createOpenTelemetry
import io.opentelemetry.kotlin.init.LogExportConfigDsl
import io.opentelemetry.kotlin.logging.export.LogRecordProcessor

/** What the collector files the app's logs under. It checks for exactly this. */
const val TELEMETRY_SERVICE_NAME = "household-hub-app"

private const val INSTRUMENTATION_SCOPE = "com.homelab.household"

/**
 * The app's logs on their way off the phone — or, with [logWriter] null, nothing at all.
 *
 * Everything in the app logs through Kermit and knows nothing of this. [start] adds one more writer
 * beside the platform's own, so logcat and Xcode show what they always did and the lines at info
 * and above are also sent.
 */
class Telemetry(
    private val logWriter: LogWriter?,
) {
    val isEnabled: Boolean get() = logWriter != null

    var isStarted: Boolean = false
        private set

    /**
     * Installs the writer and says the app has started. Called by `HouseholdHubSdk.init`, and a
     * second call does nothing: a writer installed twice would send every line twice. The line is
     * logged whether or not anything is sent, so it is in logcat either way.
     */
    fun start(
        install: (LogWriter) -> Unit = { Logger.addLogWriter(it) },
        log: Logger = Logger.withTag("App"),
    ) {
        if (isStarted) return
        isStarted = true
        logWriter?.let(install)
        log.i { "App started" }
    }
}

/**
 * Starts the OpenTelemetry SDK for logs and returns the Kermit writer that feeds it, or null —
 * without starting anything, and without calling [processor] — when [config] has no endpoint.
 *
 * Logs are the only signal: no tracer or meter is configured, so no record carries a trace id.
 * [processor] is where records go next; the app passes a batching processor around the OTLP
 * exporter, and it is a parameter so that this file needs no HTTP client to be read or tested.
 */
@OptIn(ExperimentalApi::class)
fun createTelemetryLogWriter(
    config: TelemetryConfig,
    device: Map<String, String> = deviceAttributes(),
    processor: LogExportConfigDsl.() -> LogRecordProcessor,
): LogWriter? {
    if (!config.isEnabled) return null
    val openTelemetry =
        createOpenTelemetry {
            serviceName = TELEMETRY_SERVICE_NAME
            resource(device + ("app.version" to config.appVersion))
            loggerProvider { export(processor) }
        }
    return OtelLogWriter(openTelemetry.loggerProvider.getLogger(INSTRUMENTATION_SCOPE))
}

/**
 * The headers every export carries: the token of whoever is signed in right now, and nothing when
 * nobody is. Read from [storage] each time it is asked, never kept — the same rule, for the same
 * reason, as `installBearerAuth`. No member id: the collector reads that off the token itself.
 */
fun telemetryHeaders(storage: StoredSessionLocalDataSource): Map<String, String> =
    storage.getAccessToken()?.let { mapOf("Authorization" to "Bearer $it") } ?: emptyMap()

/**
 * What this device is, for the resource every record carries: `os.name`, `os.version` and
 * `device.model.identifier`. Nothing that identifies one device among others of its kind.
 */
expect fun deviceAttributes(): Map<String, String>
