package com.homelab.household.data.repository

import com.homelab.household.data.datasource.local.AuthEventsLocalDataSource
import com.homelab.household.data.datasource.local.StoredSessionLocalDataSource
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Orchestration and mapping. Every call goes out through [remote], every DTO becomes a domain model
 * here, and what this phone keeps — its token and the member it belongs to — lives in [storage].
 * Nothing in this file knows that the hub speaks HTTP.
 */
class AuthRepositoryImpl(
    private val remote: AuthRemoteDataSource,
    private val storage: StoredSessionLocalDataSource,
    private val events: AuthEventsLocalDataSource,
    private val hubConfig: HubConfig,
) : AuthRepository {
    private val refreshMutex = Mutex()
    private var activeRefresh: CompletableDeferred<String>? = null

    override suspend fun login(
        memberId: String,
        pin: String,
    ): User = signedIn(remote.login(memberId, pin))

    override suspend fun onboard(
        name: String,
        pin: String,
        avatarColor: String,
    ): User = signedIn(remote.onboard(name, pin, avatarColor))

    override suspend fun joinHousehold(
        code: String,
        fullName: String,
        pin: String,
        avatarColor: String,
    ): User = signedIn(remote.joinHousehold(code, fullName, pin, avatarColor))

    override suspend fun redeemPinReset(
        code: String,
        pin: String,
    ): User = signedIn(remote.redeemPinReset(code, pin))

    override suspend fun lookUpInvite(code: String): InvitePreview =
        InviteDataMapper.toPreview(remote.lookUpInvite(code))

    override suspend fun listMembers(): List<Member> = remote.listMembers().map(UserDataMapper::toMember)

    override suspend fun checkStatus(): AuthStatus =
        remote.checkStatus().let { AuthStatus(isInitialized = it.is_initialized, memberCount = it.member_count) }

    /**
     * Whoever is signed in, as this phone recorded it the last time the hub said so. No hub is
     * needed for that, which is the point: the profile has a name and a colour to draw on a cold
     * start with nothing to ask.
     *
     * The hub is asked only when the phone has a token but no member beside it — an app updated
     * from a version that kept only the token. A hub that will not answer then means nobody is
     * signed in rather than an error, because every caller of this is deciding which screen to open.
     */
    override suspend fun getCurrentUser(): User? {
        if (storage.getAccessToken() == null) return null
        storage.getUser()?.let { return UserDataMapper.toDomain(it) }

        return runCatchingSafe { remote.fetchCurrentUser().also(storage::saveUser) }
            .getOrNull()
            ?.let(UserDataMapper::toDomain)
    }

    override fun hasStoredSession(): Boolean = storage.getAccessToken() != null

    /** One call: the token and the member it belongs to are kept in the same place. */
    override suspend fun logout() = storage.clear()

    override fun observeSignedOut(): Flow<Unit> = events.observeSignedOut()

    /**
     * One renewal at a time: callers who ask while one is on its way wait for its answer. A failure
     * reaches every caller through the shared answer, without cancelling whoever started it.
     */
    override suspend fun refreshToken(): String {
        val (renewal, startedHere) =
            refreshMutex.withLock {
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
        val kept =
            storage.getAccessToken()
                ?: throw UnauthorizedException("Nobody is signed in on this phone")
        val fresh = remote.renew(kept)
        signedIn(fresh)
        return fresh.access_token
    }

    override fun getHubHost(): String = hubConfig.baseUrl.substringAfter("://").substringBefore("/")

    /**
     * Keeps the token and who it belongs to, for every way of signing in — and for renewal, which
     * is why the stored member is refreshed whenever the hub hands over a new token.
     */
    private fun signedIn(response: TokenResponseDto): User {
        storage.saveTokens(response.access_token)
        val user =
            response.user
                ?: throw UpstreamGatewayException(statusCode = 200, message = "The hub signed in without saying who")
        storage.saveUser(user)
        return UserDataMapper.toDomain(user)
    }
}
