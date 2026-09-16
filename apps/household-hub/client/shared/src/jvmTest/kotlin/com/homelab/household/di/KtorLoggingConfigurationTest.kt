package com.homelab.household.di

import com.homelab.household.data.local.InMemoryTokenStorage
import com.homelab.household.data.local.TokenStorage
import com.homelab.household.data.remote.HubConfig
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
                    single<TokenStorage> { InMemoryTokenStorage() }
                }
            )
        }

        val client = get<HttpClient>()
        assertEquals(LogLevel.ALL, client.loggingLevel())
    }

    @Test
    fun logs_info_when_isDebug_is_false() {
        startKoin {
            modules(
                sdkModules(HubConfig(baseUrl = "https://hub.test", isDebug = false)) + module {
                    single<HttpClientEngine> { MockEngine { respond("{}", HttpStatusCode.OK) } }
                    single<TokenStorage> { InMemoryTokenStorage() }
                }
            )
        }

        val client = get<HttpClient>()
        assertEquals(LogLevel.INFO, client.loggingLevel())
    }

    private fun HttpClient.loggingLevel(): LogLevel {
        val plugin = plugin(Logging)
        val levelField = plugin::class.java.getDeclaredField("config").apply { isAccessible = true }
        val config = levelField.get(plugin) as io.ktor.client.plugins.logging.LoggingConfig
        return config.level
    }
}
