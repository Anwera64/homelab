package com.homelab.household.sdk

import com.homelab.household.di.appModules
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

object HouseholdHubSdk {

    fun init(appDeclaration: KoinAppDeclaration? = null): KoinApplication {
        return startKoin {
            appDeclaration?.invoke(this)
            modules(appModules)
        }
    }
}
