package com.homelab.household.sdk

import com.homelab.household.data.di.DEFAULT_BASE_URL
import com.homelab.household.data.network.HubConfig
import com.homelab.household.data.telemetry.Telemetry
import com.homelab.household.data.telemetry.TelemetryConfig
import com.homelab.household.di.appModules
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

fun sdkModules(
    hubConfig: HubConfig,
    telemetryConfig: TelemetryConfig = TelemetryConfig.Disabled,
): List<Module> =
    listOf(
        module {
            single { hubConfig }
            single { telemetryConfig }
        },
    ) + appModules

object HouseholdHubSdk {
    /**
     * Starts the graph, then telemetry: the log writer is installed and "App started" is logged.
     *
     * [telemetryConfig] defaults to off, and only the two real apps pass one. That is deliberate —
     * a test, a preview or a tool that starts the graph must not post to the household's collector
     * just by starting.
     */
    fun init(
        hubConfig: HubConfig = HubConfig(DEFAULT_BASE_URL),
        telemetryConfig: TelemetryConfig = TelemetryConfig.Disabled,
        appDeclaration: KoinAppDeclaration? = null,
    ): KoinApplication =
        startKoin {
            appDeclaration?.invoke(this)
            modules(sdkModules(hubConfig, telemetryConfig))
        }.also { it.koin.get<Telemetry>().start() }
}
