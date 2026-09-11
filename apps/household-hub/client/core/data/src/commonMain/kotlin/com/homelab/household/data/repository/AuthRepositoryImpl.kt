package com.homelab.household.data.repository

import com.homelab.household.data.dto.AuthStatusDto
import com.homelab.household.data.dto.TokenResponseDto
import com.homelab.household.data.dto.UserLoginRequestDto
import com.homelab.household.data.dto.UserOnboardRequestDto
import com.homelab.household.data.dto.UserReadDto
import com.homelab.household.data.local.TokenStorage
import com.homelab.household.data.mapper.UserDataMapper
import com.homelab.household.data.remote.NetworkExceptionHelper
import com.homelab.household.domain.model.AuthStatus
import com.homelab.household.domain.model.User
import com.homelab.household.domain.repository.AuthRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AuthRepositoryImpl(
    private val client: HttpClient,
    private val tokenStorage: TokenStorage,
    private val baseUrl: String
) : AuthRepository {

    private val _currentUserFlow = MutableStateFlow<User?>(null)
    private val refreshMutex = Mutex()
    private var activeRefresh: Deferred<String>? = null

    override suspend fun login(username: String, password: String): User {
        val response = client.post("$baseUrl/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(UserLoginRequestDto(username = username, password = password))
        }.body<TokenResponseDto>()

        tokenStorage.saveTokens(response.access_token)
        val user = response.user?.let { UserDataMapper.toDomain(it) }
            ?: User(id = "", username = username, email = "", fullName = username, isAdmin = false, isActive = true)
        _currentUserFlow.value = user
        return user
    }

    override suspend fun onboard(
        username: String,
        email: String,
        password: String,
        fullName: String,
        avatarColor: String?
    ): User {
        val response = client.post("$baseUrl/api/v1/auth/onboard") {
            contentType(ContentType.Application.Json)
            setBody(
                UserOnboardRequestDto(
                    username = username,
                    email = email,
                    password = password,
                    fullName = fullName,
                    avatarColor = avatarColor
                )
            )
        }.body<TokenResponseDto>()

        tokenStorage.saveTokens(response.access_token)
        val user = response.user?.let { UserDataMapper.toDomain(it) }
            ?: User(id = "", username = username, email = email, fullName = fullName, isAdmin = true, isActive = true)
        _currentUserFlow.value = user
        return user
    }

    override suspend fun checkStatus(): AuthStatus {
        val dto = try {
            client.get("$baseUrl/api/v1/auth/status").body<AuthStatusDto>()
        } catch (e: Exception) {
            NetworkExceptionHelper.rethrowAsDomain(e)
        }
        return AuthStatus(
            isInitialized = dto.is_initialized,
            memberCount = dto.member_count
        )
    }

    override suspend fun getCurrentUser(): User? {
        val cached = _currentUserFlow.value
        if (cached != null) return cached

        val token = tokenStorage.getAccessToken() ?: return null
        return try {
            val dto = client.get("$baseUrl/api/v1/auth/me") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }.body<UserReadDto>()
            val user = UserDataMapper.toDomain(dto)
            _currentUserFlow.value = user
            user
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun logout() {
        tokenStorage.clear()
        _currentUserFlow.value = null
    }

    override fun observeCurrentUser(): Flow<User?> = _currentUserFlow.asStateFlow()

    override suspend fun refreshToken(): String {
        val deferred = refreshMutex.withLock {
            activeRefresh ?: CoroutineScope(currentCoroutineContext()).async {
                try {
                    val token = tokenStorage.getRefreshToken() ?: tokenStorage.getAccessToken() ?: ""
                    val response = client.post("$baseUrl/api/v1/auth/refresh") {
                        contentType(ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }.body<TokenResponseDto>()

                    tokenStorage.saveTokens(response.access_token)
                    response.user?.let {
                        _currentUserFlow.value = UserDataMapper.toDomain(it)
                    }
                    response.access_token
                } finally {
                    refreshMutex.withLock {
                        activeRefresh = null
                    }
                }
            }.also { activeRefresh = it }
        }
        return deferred.await()
    }
}
