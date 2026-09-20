package com.homelab.household.data.di

import com.homelab.household.data.BuildConfig
import com.homelab.household.data.datasource.local.AuthSessionLocalDataSource
import com.homelab.household.data.datasource.local.TokenLocalDataSource
import com.homelab.household.data.datasource.remote.AgentRemoteDataSource
import com.homelab.household.data.datasource.remote.AuthRemoteDataSource
import com.homelab.household.data.datasource.remote.GossipRemoteDataSource
import com.homelab.household.data.datasource.remote.KtorAgentRemoteDataSource
import com.homelab.household.data.datasource.remote.KtorAuthRemoteDataSource
import com.homelab.household.data.datasource.remote.KtorGossipRemoteDataSource
import com.homelab.household.data.datasource.remote.KtorMembersRemoteDataSource
import com.homelab.household.data.datasource.remote.KtorMemoryRemoteDataSource
import com.homelab.household.data.datasource.remote.KtorServerStatusRemoteDataSource
import com.homelab.household.data.datasource.remote.KtorSpaceRemoteDataSource
import com.homelab.household.data.datasource.remote.MembersRemoteDataSource
import com.homelab.household.data.datasource.remote.MemoryRemoteDataSource
import com.homelab.household.data.datasource.remote.ServerStatusRemoteDataSource
import com.homelab.household.data.datasource.remote.SpaceRemoteDataSource
import com.homelab.household.data.network.HubConfig
import com.homelab.household.data.network.KermitKtorLogger
import com.homelab.household.data.network.PublicEndpoints
import com.homelab.household.data.network.signOutOnUnauthorized
import com.homelab.household.data.remote.DefensiveSseStreamReader
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
        val tokenStorage: TokenLocalDataSource = get()
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
            val session: AuthSessionLocalDataSource = get()
            signOutOnUnauthorized(tokenStorage) { session.raiseSignedOut() }
        }
    }
    single { AuthSessionLocalDataSource() }
    single<AuthRemoteDataSource> { KtorAuthRemoteDataSource(get(), get<HubConfig>().baseUrl) }
    single<MembersRemoteDataSource> { KtorMembersRemoteDataSource(get(), get<HubConfig>().baseUrl) }
    single<AgentRemoteDataSource> { KtorAgentRemoteDataSource(get(), get<HubConfig>().baseUrl) }
    single<SpaceRemoteDataSource> { KtorSpaceRemoteDataSource(get(), get<HubConfig>().baseUrl) }
    single<MemoryRemoteDataSource> { KtorMemoryRemoteDataSource(get(), get<HubConfig>().baseUrl) }
    single<GossipRemoteDataSource> { KtorGossipRemoteDataSource(get(), get<HubConfig>().baseUrl) }
    single { DefensiveSseStreamReader(get()) }
    single<ServerStatusRemoteDataSource> { KtorServerStatusRemoteDataSource(get(), get<HubConfig>().baseUrl) }

    single<AuthRepository> { AuthRepositoryImpl(get(), get(), get(), get()) }
    single<MembersRepository> { MembersRepositoryImpl(get(), get()) }
    single<SessionRepository> { SessionRepositoryImpl(get(), get<HubConfig>().baseUrl, 1000L, get()) }
    single<ServerStatusRepository> { ServerStatusRepositoryImpl(get()) }
    single<AgentRepository> { AgentRepositoryImpl(get()) }
    single<SpaceRepository> { SpaceRepositoryImpl(get()) }
    single<MemoryRepository> { MemoryRepositoryImpl(get()) }
    single<GossipRepository> { GossipRepositoryImpl(get()) }
}
