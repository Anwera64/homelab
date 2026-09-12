package com.homelab.household.app.testing

import com.homelab.household.domain.model.AuthStatus
import com.homelab.household.domain.model.User
import com.homelab.household.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Screen tests go through the real ViewModel and use cases; only the hub itself is faked. */
class FakeAuthRepository(
    private val status: () -> AuthStatus,
    private val hubHost: String = "hub.spicy-llama.duckdns.org"
) : AuthRepository {

    override suspend fun checkStatus(): AuthStatus = status()

    override fun getHubHost(): String = hubHost

    override suspend fun login(username: String, password: String): User = unsupported()

    override suspend fun onboard(
        username: String,
        email: String,
        password: String,
        fullName: String,
        avatarColor: String?
    ): User = unsupported()

    override suspend fun getCurrentUser(): User? = null

    override suspend fun logout() = Unit

    override fun observeCurrentUser(): Flow<User?> = flowOf(null)

    override suspend fun refreshToken(): String = unsupported()

    private fun unsupported(): Nothing = throw UnsupportedOperationException("Not used by screen tests")
}
