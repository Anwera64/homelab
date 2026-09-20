package com.homelab.household.di

import com.homelab.household.data.datasource.local.InMemoryTokenStorage
import com.homelab.household.data.datasource.local.TokenLocalDataSource
import com.homelab.household.data.network.HubConfig
import com.homelab.household.sdk.sdkModules
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.plugin
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.get

class KtorLoggingConfigurationTest : KoinTest {

    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun logs_all_when_isDebug_is_true() {
        startKoin {
            modules(
                sdkModules(HubConfig(baseUrl = "https://hub.test", isDebug = true)) + module {
                    single<HttpClientEngine> { MockEngine { respond("{}", HttpStatusCode.OK) } }
                    single<TokenLocalDataSource> { InMemoryTokenStorage() }
                }
            )
        }

        val client = get<HttpClient>()
        assertEquals(LogLevel.ALL, client.loggingLevel())
        assertTrue(client.logger() is com.homelab.household.data.network.KermitKtorLogger)
    }

    @Test
    fun logs_info_when_isDebug_is_false() {
        startKoin {
            modules(
                sdkModules(HubConfig(baseUrl = "https://hub.test", isDebug = false)) + module {
                    single<HttpClientEngine> { MockEngine { respond("{}", HttpStatusCode.OK) } }
                    single<TokenLocalDataSource> { InMemoryTokenStorage() }
                }
            )
        }

        val client = get<HttpClient>()
        assertEquals(LogLevel.INFO, client.loggingLevel())
        assertTrue(client.logger() is com.homelab.household.data.network.KermitKtorLogger)
    }

    private fun HttpClient.loggingLevel(): LogLevel = loggingConfig().level

    private fun HttpClient.logger(): io.ktor.client.plugins.logging.Logger = loggingConfig().logger

    private fun HttpClient.loggingConfig(): io.ktor.client.plugins.logging.LoggingConfig {
        val plugin = plugin(Logging)
        val levelField = plugin::class.java.getDeclaredField("config").apply { isAccessible = true }
        return levelField.get(plugin) as io.ktor.client.plugins.logging.LoggingConfig
    }
}
