package com.homelab.household.di

import com.homelab.household.data.datasource.local.KeychainSessionStorage
import com.homelab.household.data.datasource.local.StoredSessionLocalDataSource
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module =
    module {
        single<HttpClientEngine> {
            Darwin.create {
                configureSession {
                    // A long answer can pause between tokens; NSURLSession's default 60s
                    // timeoutIntervalForRequest measures the gap between bytes, so it would cut
                    // the stream just like OkHttp's read timeout does on Android. Unlike OkHttp,
                    // 0 does not mean "no timeout" here, so these are large finite values instead.
                    timeoutIntervalForRequest = 3600.0
                    timeoutIntervalForResource = 86400.0
                }
            }
        }
        single<StoredSessionLocalDataSource> { KeychainSessionStorage() }
    }
