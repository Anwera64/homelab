package com.homelab.household.data.datasource.remote

import com.homelab.household.data.datasource.remote.`interface`.AuthRemoteDataSource
import com.homelab.household.data.dto.AuthStatusDto
import com.homelab.household.data.dto.InvitePreviewReadDto
import com.homelab.household.data.dto.InviteRedeemRequestDto
import com.homelab.household.data.dto.MemberProfileDto
import com.homelab.household.data.dto.PinResetRedeemRequestDto
import com.homelab.household.data.dto.TokenResponseDto
import com.homelab.household.data.dto.UserLoginRequestDto
import com.homelab.household.data.dto.UserOnboardRequestDto
import com.homelab.household.data.dto.UserReadDto
import com.homelab.household.data.network.codeGuessesLocked
import com.homelab.household.data.network.ensureJsonSuccess
import com.homelab.household.data.network.reachingHub
import com.homelab.household.data.network.throwIfSignInRefused
import com.homelab.household.domain.exception.HubAlreadySetUpException
import com.homelab.household.domain.exception.InviteInvalidException
import com.homelab.household.domain.exception.NameTakenException
import com.homelab.household.domain.exception.UnauthorizedException
import com.homelab.household.domain.exception.ValidationException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

class KtorAuthRemoteDataSource(
    private val client: HttpClient,
    private val baseUrl: String,
) : AuthRemoteDataSource {

    override suspend fun login(memberId: String, pin: String): TokenResponseDto = reachingHub {
        val response = client.post("$baseUrl/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(UserLoginRequestDto(userId = memberId, pin = pin))
        }
        response.throwIfSignInRefused()
        response.ensureJsonSuccess().body()
    }

    override suspend fun onboard(name: String, pin: String, avatarColor: String): TokenResponseDto = reachingHub {
        val response = client.post("$baseUrl/api/v1/auth/register-initial") {
            contentType(ContentType.Application.Json)
            setBody(UserOnboardRequestDto(fullName = name, pin = pin, avatarColor = avatarColor))
        }
        when (response.status) {
            HttpStatusCode.BadRequest -> throw HubAlreadySetUpException()
            HttpStatusCode.UnprocessableEntity -> throw ValidationException("The hub refused that name or PIN")
        }
        response.ensureJsonSuccess().body()
    }

    override suspend fun lookUpInvite(code: String): InvitePreviewReadDto = reachingHub {
        val response = client.get("$baseUrl/api/v1/invites/$code")
        when (response.status) {
            HttpStatusCode.BadRequest -> throw InviteInvalidException()
            HttpStatusCode.TooManyRequests -> throw response.codeGuessesLocked()
        }
        response.ensureJsonSuccess().body()
    }

    override suspend fun joinHousehold(
        code: String,
        fullName: String,
        pin: String,
        avatarColor: String,
    ): TokenResponseDto = reachingHub {
        val response = client.post("$baseUrl/api/v1/invites/$code/redeem") {
            contentType(ContentType.Application.Json)
            setBody(InviteRedeemRequestDto(fullName = fullName, pin = pin, avatarColor = avatarColor))
        }
        when (response.status) {
            HttpStatusCode.BadRequest -> throw InviteInvalidException()
            HttpStatusCode.Conflict -> throw NameTakenException()
            HttpStatusCode.TooManyRequests -> throw response.codeGuessesLocked()
        }
        response.ensureJsonSuccess().body()
    }

    override suspend fun redeemPinReset(code: String, pin: String): TokenResponseDto = reachingHub {
        val response = client.post("$baseUrl/api/v1/auth/pin-resets/$code/redeem") {
            contentType(ContentType.Application.Json)
            setBody(PinResetRedeemRequestDto(pin = pin))
        }
        when (response.status) {
            HttpStatusCode.BadRequest -> throw InviteInvalidException()
            HttpStatusCode.TooManyRequests -> throw response.codeGuessesLocked()
        }
        response.ensureJsonSuccess().body()
    }

    override suspend fun listMembers(): List<MemberProfileDto> = reachingHub {
        client.get("$baseUrl/api/v1/auth/members")
            .ensureJsonSuccess()
            .body<List<MemberProfileDto>>()
    }

    override suspend fun checkStatus(): AuthStatusDto = reachingHub {
        client.get("$baseUrl/api/v1/auth/status").ensureJsonSuccess().body()
    }

    override suspend fun fetchCurrentUser(): UserReadDto = reachingHub {
        client.get("$baseUrl/api/v1/auth/me").ensureJsonSuccess().body()
    }

    override suspend fun renew(accessToken: String): TokenResponseDto = reachingHub {
        // No separate refresh token: the hub re-issues one it still accepts.
        val response = client.post("$baseUrl/api/v1/auth/refresh") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        if (response.status == HttpStatusCode.Unauthorized) {
            throw UnauthorizedException("The hub no longer accepts this token")
        }
        response.ensureJsonSuccess().body()
    }
}
