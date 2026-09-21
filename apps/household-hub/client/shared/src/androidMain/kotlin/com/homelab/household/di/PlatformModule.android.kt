package com.homelab.household.di

import com.homelab.household.data.datasource.local.KeystoreSessionStorage
import com.homelab.household.data.datasource.local.StoredSessionLocalDataSource
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import java.time.Duration
import java.util.concurrent.TimeUnit
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<HttpClientEngine> {
        OkHttp.create {
            config {
                connectTimeout(10, TimeUnit.SECONDS)
                // A long answer can pause between tokens; OkHttp's 10s read timeout would
                // cut the stream and look like a dropped connection.
                readTimeout(Duration.ZERO)
                callTimeout(Duration.ZERO)
                retryOnConnectionFailure(true)
            }
        }
    }
    single<StoredSessionLocalDataSource> { KeystoreSessionStorage(androidContext()) }
}
