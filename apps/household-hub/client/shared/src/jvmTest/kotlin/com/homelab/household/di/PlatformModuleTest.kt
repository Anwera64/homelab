package com.homelab.household.di

import com.homelab.household.data.local.FileTokenStorage
import com.homelab.household.data.local.TokenStorage
import com.homelab.household.sdk.HouseholdHubSdk
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIOEngineConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.get

class PlatformModuleTest : KoinTest {

    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun jvm_platform_module_binds_cio_engine_and_file_token_storage() {
        HouseholdHubSdk.init()

        val engine = get<HttpClientEngine>()
        assertTrue(engine.config is CIOEngineConfig, "Expected a CIO engine on the JVM, got ${engine::class.simpleName}")
        assertTrue(get<TokenStorage>() is FileTokenStorage)
    }
}
