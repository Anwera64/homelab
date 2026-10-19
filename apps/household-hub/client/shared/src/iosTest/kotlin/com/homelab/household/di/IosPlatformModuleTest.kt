package com.homelab.household.di

import com.homelab.household.data.datasource.local.KeychainSessionStorage
import com.homelab.household.data.datasource.local.StoredSessionLocalDataSource
import com.homelab.household.domain.repository.AgentRepository
import com.homelab.household.domain.repository.AuthRepository
import com.homelab.household.domain.repository.MemoryRepository
import com.homelab.household.domain.repository.ServerStatusRepository
import com.homelab.household.domain.repository.SessionRepository
import com.homelab.household.domain.repository.SpaceRepository
import com.homelab.household.domain.usecase.CheckAuthStatusUseCase
import com.homelab.household.domain.usecase.GetSessionUseCase
import com.homelab.household.domain.usecase.LoginUseCase
import com.homelab.household.domain.usecase.StreamChatTurnUseCase
import com.homelab.household.presentation.chatsession.ChatSessionViewModel
import com.homelab.household.presentation.dashboard.DashboardViewModel
import com.homelab.household.presentation.launch.LaunchViewModel
import com.homelab.household.presentation.memories.MemoriesViewModel
import com.homelab.household.sdk.HouseholdHubSdk
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.DarwinClientEngineConfig
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.get
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IosPlatformModuleTest : KoinTest {
    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `GIVEN the SDK started on iOS WHEN the engine and token storage are resolved THEN they are the Darwin engine and the Keychain storage`() {
        HouseholdHubSdk.init()

        val engine = get<HttpClientEngine>()
        assertTrue(
            engine.config is DarwinClientEngineConfig,
            "Expected a Darwin engine on iOS, got ${engine::class.simpleName}",
        )
        assertTrue(get<StoredSessionLocalDataSource>() is KeychainSessionStorage)
    }

    @Test
    fun `GIVEN the SDK started on iOS WHEN every repository use case and view model is resolved THEN all of them resolve`() {
        val koinApp = HouseholdHubSdk.init()

        assertNotNull(koinApp)

        // Platform
        assertNotNull(get<HttpClientEngine>())
        assertNotNull(get<StoredSessionLocalDataSource>())

        // Repositories
        assertNotNull(get<AuthRepository>())
        assertNotNull(get<SessionRepository>())
        assertNotNull(get<ServerStatusRepository>())
        assertNotNull(get<AgentRepository>())
        assertNotNull(get<SpaceRepository>())
        assertNotNull(get<MemoryRepository>())

        // Use cases
        assertNotNull(get<LoginUseCase>())
        assertNotNull(get<GetSessionUseCase>())
        assertNotNull(get<StreamChatTurnUseCase>())
        assertNotNull(get<CheckAuthStatusUseCase>())

        // ViewModels
        assertNotNull(get<ChatSessionViewModel>())
        assertNotNull(get<DashboardViewModel>())
        assertNotNull(get<LaunchViewModel>())
        assertNotNull(get<MemoriesViewModel>())
    }
}
