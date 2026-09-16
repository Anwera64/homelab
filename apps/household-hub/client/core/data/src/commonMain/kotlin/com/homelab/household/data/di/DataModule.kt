package com.homelab.household.data.di

import com.homelab.household.data.local.TokenStorage
import com.homelab.household.data.remote.DefensiveSseStreamReader
import com.homelab.household.data.remote.HubConfig
import com.homelab.household.data.remote.KermitKtorLogger
import com.homelab.household.data.remote.PublicEndpoints
import com.homelab.household.data.remote.ServerHealthMonitor
import com.homelab.household.data.remote.SignedOutSignal
import com.homelab.household.data.remote.signOutOnUnauthorized
import com.homelab.household.data.repository.AgentRepositoryImpl
import com.homelab.household.data.repository.AuthRepositoryImpl
import com.homelab.household.data.repository.GossipRepositoryImpl
import com.homelab.household.data.repository.MembersRepositoryImpl
import com.homelab.household.data.repository.MemoryRepositoryImpl
import com.homelab.household.data.repository.ServerStatusRepositoryImpl
import com.homelab.household.data.repository.SessionRepositoryImpl
import com.homelab.household.data.repository.SpaceRepositoryImpl
import com.homelab.household.domain.repository.AgentRepository
import com.homelab.household.domain.repository.AuthRepository
import com.homelab.household.domain.repository.GossipRepository
import com.homelab.household.domain.repository.MembersRepository
import com.homelab.household.domain.repository.MemoryRepository
import com.homelab.household.domain.repository.ServerStatusRepository
import com.homelab.household.domain.repository.SessionRepository
import com.homelab.household.domain.repository.SpaceRepository
import com.homelab.household.data.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.dsl.module

const val DEFAULT_BASE_URL = BuildConfig.BASE_URL

val dataModule = module {
    single {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            prettyPrint = false
        }
    }

    single {
        val tokenStorage: TokenStorage = get()
        val jsonSerializer: Json = get()
        val hubConfig: HubConfig = get()
        HttpClient(get<HttpClientEngine>()) {
            install(ContentNegotiation) {
                json(jsonSerializer)
            }
            install(Logging) {
                logger = KermitKtorLogger()
                level = if (hubConfig.isDebug) {
                    LogLevel.ALL
                } else {
                    LogLevel.INFO
                }
            }
            install(Auth) {
                bearer {
                    loadTokens {
                        val access = tokenStorage.getAccessToken()
                        val refresh = tokenStorage.getRefreshToken()
                        if (access != null) {
                            BearerTokens(accessToken = access, refreshToken = refresh ?: "")
                        } else {
                            null
                        }
                    }
                    sendWithoutRequest { request -> !PublicEndpoints.isPublic(request.url.buildString()) }
                }
            }
            val signedOut: SignedOutSignal = get()
            signOutOnUnauthorized(tokenStorage) { signedOut.raise() }
        }
    }
    single { SignedOutSignal() }
    single { DefensiveSseStreamReader(get()) }
    single { ServerHealthMonitor(get(), get<HubConfig>().baseUrl) }

    single<AuthRepository> { AuthRepositoryImpl(get(), get(), get<HubConfig>().baseUrl, get()) }
    single<MembersRepository> { MembersRepositoryImpl(get(), get(), get<HubConfig>().baseUrl) }
    single<SessionRepository> { SessionRepositoryImpl(get(), get<HubConfig>().baseUrl, 1000L, get()) }
    single<ServerStatusRepository> { ServerStatusRepositoryImpl(get()) }
    single<AgentRepository> { AgentRepositoryImpl(get(), get<HubConfig>().baseUrl) }
    single<SpaceRepository> { SpaceRepositoryImpl(get(), get<HubConfig>().baseUrl) }
    single<MemoryRepository> { MemoryRepositoryImpl(get(), get<HubConfig>().baseUrl) }
    single<GossipRepository> { GossipRepositoryImpl(get(), get<HubConfig>().baseUrl) }
}
