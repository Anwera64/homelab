package com.homelab.household.domain.repository

import com.homelab.household.domain.model.AuthStatus
import com.homelab.household.domain.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun login(username: String, password: String): User
    suspend fun onboard(username: String, email: String, password: String, fullName: String, avatarColor: String? = null): User
    suspend fun checkStatus(): AuthStatus
    suspend fun getCurrentUser(): User?
    suspend fun logout()
    fun observeCurrentUser(): Flow<User?>
    suspend fun refreshToken(): String
    fun getHubHost(): String
}
