package com.homelab.household

import android.app.Application
import com.homelab.household.data.network.HubConfig
import com.homelab.household.data.telemetry.TelemetryConfig
import com.homelab.household.sdk.HouseholdHubSdk
import org.koin.android.ext.koin.androidContext

class HouseholdHubApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        HouseholdHubSdk.init(
            hubConfig =
                HubConfig(
                    baseUrl = BuildConfig.HUB_URL,
                    isDebug = BuildConfig.DEBUG,
                ),
            telemetryConfig =
                TelemetryConfig(
                    endpoint = BuildConfig.TELEMETRY_URL,
                    appVersion = BuildConfig.VERSION_NAME,
                ),
        ) {
            androidContext(this@HouseholdHubApplication)
        }
    }
}
