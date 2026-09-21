package com.homelab.household.data.repository

import com.homelab.household.data.datasource.local.AuthSessionLocalDataSource
import com.homelab.household.data.datasource.local.TokenLocalDataSource
import com.homelab.household.data.datasource.remote.`interface`.AuthRemoteDataSource
import com.homelab.household.data.dto.TokenResponseDto
import com.homelab.household.data.mapper.InviteDataMapper
import com.homelab.household.data.mapper.UserDataMapper
import com.homelab.household.data.network.HubConfig
import com.homelab.household.domain.exception.UnauthorizedException
import com.homelab.household.domain.exception.UpstreamGatewayException
import com.homelab.household.domain.model.AuthStatus
import com.homelab.household.domain.model.InvitePreview
import com.homelab.household.domain.model.Member
import com.homelab.household.domain.model.User
import com.homelab.household.domain.repository.AuthRepository
import com.homelab.household.domain.util.runCatchingSafe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Orchestration and mapping. Every call goes out through [remote], every DTO becomes a domain model
 * here, and what this phone keeps lives in [tokenStorage] and [session]. Nothing in this file knows
 * that the hub speaks HTTP.
 */
class AuthRepositoryImpl(
    private val remote: AuthRemoteDataSource,
    private val tokenStorage: TokenLocalDataSource,
    private val session: AuthSessionLocalDataSource,
    private val hubConfig: HubConfig,
) : AuthRepository {

    private val refreshMutex = Mutex()
    private var activeRefresh: CompletableDeferred<String>? = null

    override suspend fun login(memberId: String, pin: String): User =
        signedIn(remote.login(memberId, pin))

    override suspend fun onboard(name: String, pin: String, avatarColor: String): User =
        signedIn(remote.onboard(name, pin, avatarColor))

    override suspend fun joinHousehold(code: String, fullName: String, pin: String, avatarColor: String): User =
        signedIn(remote.joinHousehold(code, fullName, pin, avatarColor))

    override suspend fun redeemPinReset(code: String, pin: String): User =
        signedIn(remote.redeemPinReset(code, pin))

    override suspend fun lookUpInvite(code: String): InvitePreview =
        InviteDataMapper.toPreview(remote.lookUpInvite(code))

    override suspend fun listMembers(): List<Member> =
        remote.listMembers().map(UserDataMapper::toMember)

    override suspend fun checkStatus(): AuthStatus =
        remote.checkStatus().let { AuthStatus(isInitialized = it.is_initialized, memberCount = it.member_count) }

    /**
     * Whoever is signed in, asked of the hub only when this phone does not already know and has a
     * token worth asking with. A hub that will not answer means nobody is signed in rather than an
     * error, because every caller of this is deciding which screen to open.
     */
    override suspend fun getCurrentUser(): User? {
        session.currentUser()?.let { return UserDataMapper.toDomain(it) }
        if (tokenStorage.getAccessToken() == null) return null

        return runCatchingSafe {
            val dto = remote.fetchCurrentUser()
            session.cacheCurrentUser(dto)
            UserDataMapper.toDomain(dto)
        }.getOrNull()
    }

    override fun hasStoredSession(): Boolean = tokenStorage.getAccessToken() != null

    override suspend fun logout() {
        tokenStorage.clear()
        session.forgetCurrentUser()
    }

    override fun observeCurrentUser(): Flow<User?> =
        session.observeCurrentUser().map { it?.let(UserDataMapper::toDomain) }

    override fun observeSignedOut(): Flow<Unit> = session.observeSignedOut()

    /**
     * One renewal at a time: callers who ask while one is on its way wait for its answer. A failure
     * reaches every caller through the shared answer, without cancelling whoever started it.
     */
    override suspend fun refreshToken(): String {
        val (renewal, startedHere) = refreshMutex.withLock {
            activeRefresh?.let { it to false }
                ?: CompletableDeferred<String>().also { activeRefresh = it }.let { it to true }
        }
        if (startedHere) {
            try {
                renewal.complete(renew())
            } catch (e: Throwable) {
                renewal.completeExceptionally(e)
            } finally {
                refreshMutex.withLock { activeRefresh = null }
            }
        }
        return renewal.await()
    }

    private suspend fun renew(): String {
        val kept = tokenStorage.getAccessToken()
            ?: throw UnauthorizedException("Nobody is signed in on this phone")
        val fresh = remote.renew(kept)
        signedIn(fresh)
        return fresh.access_token
    }

    override fun getHubHost(): String =
        hubConfig.baseUrl.substringAfter("://").substringBefore("/")

    /** Keeps the token and remembers who it belongs to, for every way of signing in. */
    private fun signedIn(response: TokenResponseDto): User {
        tokenStorage.saveTokens(response.access_token)
        val user = response.user
            ?: throw UpstreamGatewayException(statusCode = 200, message = "The hub signed in without saying who")
        session.cacheCurrentUser(user)
        return UserDataMapper.toDomain(user)
    }
}
