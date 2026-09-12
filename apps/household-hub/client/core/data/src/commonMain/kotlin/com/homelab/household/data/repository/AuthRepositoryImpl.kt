package com.homelab.household.data.repository

import com.homelab.household.data.dto.AuthStatusDto
import com.homelab.household.data.dto.MemberProfileDto
import com.homelab.household.data.dto.PinRefusalDto
import com.homelab.household.data.dto.TokenResponseDto
import com.homelab.household.data.dto.UserLoginRequestDto
import com.homelab.household.data.dto.UserOnboardRequestDto
import com.homelab.household.data.dto.UserReadDto
import com.homelab.household.data.local.TokenStorage
import com.homelab.household.data.mapper.UserDataMapper
import com.homelab.household.data.remote.NetworkExceptionHelper
import com.homelab.household.domain.exception.HubAlreadySetUpException
import com.homelab.household.domain.exception.NotFoundException
import com.homelab.household.domain.exception.PinLockedException
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.exception.UnauthorizedException
import com.homelab.household.domain.exception.UnexpectedContentTypeException
import com.homelab.household.domain.exception.UpstreamGatewayException
import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.exception.WrongPinException
import com.homelab.household.domain.model.AuthStatus
import com.homelab.household.domain.model.Member
import com.homelab.household.domain.model.User
import com.homelab.household.domain.repository.AuthRepository
import com.homelab.household.domain.util.runCatchingSafe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
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

    override suspend fun login(memberId: String, pin: String): User = reachingHub {
        val response = client.post("$baseUrl/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(UserLoginRequestDto(userId = memberId, pin = pin))
        }
        when (response.status) {
            HttpStatusCode.Unauthorized -> {
                val attemptsLeft = response.refusal()?.attempts_left
                throw if (attemptsLeft != null) WrongPinException(attemptsLeft) else UnauthorizedException("Wrong PIN")
            }
            HttpStatusCode.TooManyRequests -> {
                val seconds = response.refusal()?.retry_after_seconds
                    ?: response.headers[HttpHeaders.RetryAfter]?.toIntOrNull()
                    ?: throw UpstreamGatewayException(statusCode = response.status.value)
                throw PinLockedException(seconds)
            }
        }
        signedIn(response.ensureJsonSuccess().body())
    }

    override suspend fun onboard(name: String, pin: String, avatarColor: String): User = reachingHub {
        val response = client.post("$baseUrl/api/v1/auth/register-initial") {
            contentType(ContentType.Application.Json)
            setBody(UserOnboardRequestDto(fullName = name, pin = pin, avatarColor = avatarColor))
        }
        when (response.status) {
            HttpStatusCode.BadRequest -> throw HubAlreadySetUpException()
            HttpStatusCode.UnprocessableEntity -> throw ValidationException("The hub refused that name or PIN")
        }
        signedIn(response.ensureJsonSuccess().body())
    }

    override suspend fun listMembers(): List<Member> = reachingHub {
        client.get("$baseUrl/api/v1/auth/members")
            .ensureJsonSuccess()
            .body<List<MemberProfileDto>>()
            .map(UserDataMapper::toMember)
    }

    override suspend fun checkStatus(): AuthStatus {
        val dto = reachingHub {
            client.get("$baseUrl/api/v1/auth/status").ensureJsonSuccess().body<AuthStatusDto>()
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

    override fun getHubHost(): String = baseUrl.substringAfter("://").substringBefore("/")

    private suspend fun signedIn(response: TokenResponseDto): User {
        tokenStorage.saveTokens(response.access_token)
        val user = response.user?.let(UserDataMapper::toDomain)
            ?: throw UpstreamGatewayException(statusCode = HttpStatusCode.OK.value, message = "The hub signed in without saying who")
        _currentUserFlow.value = user
        return user
    }

    /** Network failures become [ServerOfflineException]; everything else passes through as thrown. */
    private suspend fun <T> reachingHub(block: suspend () -> T): T =
        try {
            block()
        } catch (e: Exception) {
            NetworkExceptionHelper.rethrowAsDomain(e)
        }

    private suspend fun HttpResponse.refusal(): PinRefusalDto? = runCatchingSafe { body<PinRefusalDto>() }.getOrNull()

    /**
     * 502–504 is a proxy saying the hub is down or starting; 404 means the address is wrong; any
     * other failure is the hub answering badly. A success that isn't JSON is a captive portal or
     * the wrong server.
     */
    private fun HttpResponse.ensureJsonSuccess(): HttpResponse {
        if (status.value in 502..504) {
            throw ServerOfflineException(message = "Hub is offline or starting up (HTTP ${status.value})")
        }
        if (!status.isSuccess()) {
            if (status == HttpStatusCode.NotFound) {
                throw NotFoundException("Hub endpoint returned HTTP 404. Check your hub address.")
            }
            throw UpstreamGatewayException(statusCode = status.value)
        }
        val type = contentType()?.withoutParameters()
        if (type != null && type != ContentType.Application.Json) {
            throw UnexpectedContentTypeException(contentType = type.toString())
        }
        return this
    }
}
