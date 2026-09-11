package com.homelab.household.di

import com.homelab.household.data.local.FileTokenStorage
import com.homelab.household.data.local.TokenStorage
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<HttpClientEngine> { CIO.create() }
    single<TokenStorage> { FileTokenStorage() }
}
