package com.homelab.household.domain.repository

import com.homelab.household.domain.model.AuthStatus
import com.homelab.household.domain.model.InvitePreview
import com.homelab.household.domain.model.Member
import com.homelab.household.domain.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun login(memberId: String, pin: String): User
    suspend fun onboard(name: String, pin: String, avatarColor: String): User
    suspend fun listMembers(): List<Member>
    suspend fun checkStatus(): AuthStatus
    suspend fun getCurrentUser(): User?

    /** Public: before choosing a PIN, the joiner sees who invited them and the name they were invited as. */
    suspend fun lookUpInvite(code: String): InvitePreview

    /** Public: the joiner redeems their code and is signed in, exactly as [login] signs someone in. */
    suspend fun joinHousehold(code: String, fullName: String, pin: String, avatarColor: String): User

    /** Public: whoever forgot their PIN redeems the code they were given and is signed in. */
    suspend fun redeemPinReset(code: String, pin: String): User

    /** A token is kept on this phone. Doesn't ask the hub whether it still accepts it. */
    fun hasStoredSession(): Boolean
    suspend fun logout()

    /** Emits when the hub stopped accepting this phone's token; the token is already forgotten. */
    fun observeSignedOut(): Flow<Unit>

    /** A fresh token for a member still signed in, replacing the one kept. */
    suspend fun refreshToken(): String
    fun getHubHost(): String
}
