package com.homelab.household.di

import com.homelab.household.data.datasource.local.InMemorySessionStorage
import com.homelab.household.data.datasource.local.StoredSessionLocalDataSource
import com.homelab.household.data.network.HubConfig
import com.homelab.household.data.telemetry.Telemetry
import com.homelab.household.data.telemetry.TelemetryConfig
import com.homelab.household.sdk.HouseholdHubSdk
import com.homelab.household.sdk.sdkModules
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.get

/**
 * Telemetry is off unless the app that starts the SDK says where to send it. Every test that
 * starts the graph with no more than `HouseholdHubSdk.init()` therefore sends nothing anywhere —
 * which is the point of the default, and what the first test here holds it to.
 */
class TelemetryStartupTest : KoinTest {
    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `GIVEN the SDK started with no telemetry config WHEN the graph is up THEN telemetry was started and is off`() {
        // GIVEN
        val hubConfig = HubConfig(baseUrl = "https://hub.test")

        // WHEN
        HouseholdHubSdk.init(hubConfig)

        // THEN
        val telemetry = get<Telemetry>()
        assertTrue(telemetry.isStarted, "HouseholdHubSdk.init must start telemetry")
        assertFalse(telemetry.isEnabled)
    }

    @Test
    fun `GIVEN modules built with a telemetry endpoint WHEN telemetry is resolved THEN it is on`() {
        // GIVEN
        startKoin {
            modules(
                sdkModules(HubConfig(baseUrl = "https://hub.test"), TelemetryConfig("https://telemetry.test", "0.1")) +
                    module {
                        single<HttpClientEngine> { MockEngine { respond("", HttpStatusCode.OK) } }
                        single<StoredSessionLocalDataSource> { InMemorySessionStorage() }
                    },
            )
        }

        // WHEN
        val telemetry = get<Telemetry>()

        // THEN
        assertTrue(telemetry.isEnabled)
    }
}
