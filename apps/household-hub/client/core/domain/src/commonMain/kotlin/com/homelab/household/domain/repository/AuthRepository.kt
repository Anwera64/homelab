package com.homelab.household.domain.repository

import com.homelab.household.domain.model.AuthStatus
import com.homelab.household.domain.model.Member
import com.homelab.household.domain.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun login(memberId: String, pin: String): User
    suspend fun onboard(name: String, pin: String, avatarColor: String): User
    suspend fun listMembers(): List<Member>
    suspend fun checkStatus(): AuthStatus
    suspend fun getCurrentUser(): User?

    /** A token is kept on this phone. Doesn't ask the hub whether it still accepts it. */
    fun hasStoredSession(): Boolean
    suspend fun logout()
    fun observeCurrentUser(): Flow<User?>
    suspend fun refreshToken(): String
    fun getHubHost(): String
}
