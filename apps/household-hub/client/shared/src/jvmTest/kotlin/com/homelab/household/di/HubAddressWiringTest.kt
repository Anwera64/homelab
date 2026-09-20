package com.homelab.household.di

import com.homelab.household.data.local.InMemoryTokenStorage
import com.homelab.household.data.local.TokenStorage
import com.homelab.household.data.network.HubConfig
import com.homelab.household.data.datasource.remote.ServerStatusRemoteDataSource
import com.homelab.household.domain.repository.AgentRepository
import com.homelab.household.domain.repository.AuthRepository
import com.homelab.household.domain.repository.GossipRepository
import com.homelab.household.domain.repository.MemoryRepository
import com.homelab.household.domain.repository.SessionRepository
import com.homelab.household.domain.repository.SpaceRepository
import com.homelab.household.domain.util.runCatchingSafe
import com.homelab.household.sdk.sdkModules
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.get

class HubAddressWiringTest : KoinTest {

    private val requestedHosts = mutableListOf<String>()

    private val recordingEngine = MockEngine { request ->
        requestedHosts += request.url.host
        respond(
            content = "{}",
            status = HttpStatusCode.InternalServerError,
            headers = headersOf(HttpHeaders.ContentType, "application/json")
        )
    }

    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun every_repository_talks_to_the_configured_hub() = runTest {
        startKoin {
            modules(
                sdkModules(HubConfig(baseUrl = "https://hub.test")) + module {
                    single<HttpClientEngine> { recordingEngine }
                    single<TokenStorage> { InMemoryTokenStorage() }
                }
            )
        }

        val calls: Map<String, suspend () -> Unit> = mapOf(
            "AuthRepository" to { get<AuthRepository>().checkStatus() },
            "SessionRepository" to { get<SessionRepository>().listSessions() },
            "AgentRepository" to { get<AgentRepository>().listAgents() },
            "SpaceRepository" to { get<SpaceRepository>().getHouseholdSpace() },
            "MemoryRepository" to { get<MemoryRepository>().auditMemories() },
            "GossipRepository" to { get<GossipRepository>().listHouseholdMilestones() },
            "ServerStatusRemoteDataSource" to { get<ServerStatusRemoteDataSource>().checkHealth() }
        )

        calls.forEach { (name, call) ->
            val before = requestedHosts.size
            runCatchingSafe { call() }
            assertTrue(requestedHosts.size > before, "$name made no request")
        }

        requestedHosts.forEach { host -> assertEquals("hub.test", host) }
    }
}
