package com.homelab.household

import android.app.Application
import com.homelab.household.sdk.HouseholdHubSdk
import org.koin.android.ext.koin.androidContext

class HouseholdHubApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        HouseholdHubSdk.init {
            androidContext(this@HouseholdHubApplication)
        }
    }
}
