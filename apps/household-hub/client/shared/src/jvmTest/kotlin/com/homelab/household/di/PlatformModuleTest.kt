package com.homelab.household.di

import com.homelab.household.data.datasource.local.FileSessionStorage
import com.homelab.household.data.datasource.local.StoredSessionLocalDataSource
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
    fun `GIVEN the SDK started on the JVM WHEN the engine and token storage are resolved THEN they are the CIO engine and the file storage`() {
        HouseholdHubSdk.init()

        val engine = get<HttpClientEngine>()
        assertTrue(
            engine.config is CIOEngineConfig,
            "Expected a CIO engine on the JVM, got ${engine::class.simpleName}",
        )
        assertTrue(get<StoredSessionLocalDataSource>() is FileSessionStorage)
    }
}
