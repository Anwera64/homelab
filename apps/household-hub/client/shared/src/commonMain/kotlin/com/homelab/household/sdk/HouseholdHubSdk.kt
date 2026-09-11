package com.homelab.household.sdk

import com.homelab.household.data.di.DEFAULT_BASE_URL
import com.homelab.household.data.remote.HubConfig
import com.homelab.household.di.appModules
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

fun sdkModules(hubConfig: HubConfig): List<Module> =
    listOf(module { single { hubConfig } }) + appModules

object HouseholdHubSdk {

    fun init(
        hubConfig: HubConfig = HubConfig(DEFAULT_BASE_URL),
        appDeclaration: KoinAppDeclaration? = null
    ): KoinApplication {
        return startKoin {
            appDeclaration?.invoke(this)
            modules(sdkModules(hubConfig))
        }
    }
}
