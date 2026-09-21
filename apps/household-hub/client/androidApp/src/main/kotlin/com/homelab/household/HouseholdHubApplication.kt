package com.homelab.household

import android.app.Application
import com.homelab.household.data.di.DEFAULT_BASE_URL
import com.homelab.household.data.network.HubConfig
import com.homelab.household.sdk.HouseholdHubSdk
import org.koin.android.ext.koin.androidContext

class HouseholdHubApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        HouseholdHubSdk.init(
            hubConfig = HubConfig(
                baseUrl = DEFAULT_BASE_URL,
                isDebug = BuildConfig.DEBUG
            )
        ) {
            androidContext(this@HouseholdHubApplication)
        }
    }
}
