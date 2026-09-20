package com.homelab.household.data.datasource.remote

import com.homelab.household.data.dto.AuthStatusDto
import com.homelab.household.data.dto.InvitePreviewReadDto
import com.homelab.household.data.dto.MemberProfileDto
import com.homelab.household.data.dto.TokenResponseDto
import com.homelab.household.data.dto.UserReadDto

/**
 * Everything the hub is asked about who is signed in. Each function is one call: it returns the
 * DTO the hub sent, or throws the domain exception its refusal means. It keeps nothing and decides
 * nothing — `AuthRepositoryImpl` is where a DTO becomes a `User` and where a token is kept.
 *
 * This is the seam that makes the repository testable: its tests mock this and never touch Ktor.
 */
interface AuthRemoteDataSource {

    suspend fun login(memberId: String, pin: String): TokenResponseDto

    suspend fun onboard(name: String, pin: String, avatarColor: String): TokenResponseDto

    suspend fun lookUpInvite(code: String): InvitePreviewReadDto

    suspend fun joinHousehold(code: String, fullName: String, pin: String, avatarColor: String): TokenResponseDto

    suspend fun redeemPinReset(code: String, pin: String): TokenResponseDto

    suspend fun listMembers(): List<MemberProfileDto>

    suspend fun checkStatus(): AuthStatusDto

    /** Who the kept token belongs to. The bearer plugin attaches it, as for any signed-in call. */
    suspend fun fetchCurrentUser(): UserReadDto

    /**
     * A fresh token in exchange for one the hub still accepts. [accessToken] is passed rather than
     * read from storage because this call is *about* that token — the repository has already
     * decided somebody is signed in, and the data source keeps nothing of its own.
     */
    suspend fun renew(accessToken: String): TokenResponseDto
}
