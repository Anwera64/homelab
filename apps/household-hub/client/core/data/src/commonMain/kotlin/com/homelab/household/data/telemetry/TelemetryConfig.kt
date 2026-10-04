package com.homelab.household.data.telemetry

/**
 * Where the app's logs go, and what every one of them says about the build that wrote it.
 *
 * [endpoint] is the collector's base address; the exporter adds `/v1/logs` itself, so a trailing
 * slash is taken off rather than left to become `//v1/logs`. An empty endpoint is the off switch:
 * no SDK is started and no log writer is installed, which is what [Disabled] is and what every
 * test that starts the graph without asking gets.
 *
 * There is no member here on purpose. The collector works out who sent a batch from the token it
 * arrives with; the app never says.
 */
class TelemetryConfig(
    endpoint: String,
    val appVersion: String = "unknown",
) {
    val endpoint: String = endpoint.trim().trimEnd('/')

    val isEnabled: Boolean get() = endpoint.isNotEmpty()

    companion object {
        val Disabled = TelemetryConfig(endpoint = "")
    }
}
