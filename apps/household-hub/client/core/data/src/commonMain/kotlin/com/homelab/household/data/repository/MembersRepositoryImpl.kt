package com.homelab.household.data.repository

import com.homelab.household.data.dto.ChangePinRequestDto
import com.homelab.household.data.dto.InviteCreateRequestDto
import com.homelab.household.data.dto.PinRefusalDto
import com.homelab.household.data.dto.PinResetApproveRequestDto
import com.homelab.household.data.dto.LeaveHouseholdRequestDto
import com.homelab.household.data.dto.TokenResponseDto
import com.homelab.household.data.dto.UserReadDto
import com.homelab.household.data.local.TokenStorage
import com.homelab.household.data.mapper.InviteDataMapper
import com.homelab.household.data.mapper.PinResetDataMapper
import com.homelab.household.data.mapper.UserDataMapper
import com.homelab.household.data.remote.NetworkExceptionHelper
import com.homelab.household.domain.exception.ForbiddenException
import com.homelab.household.domain.exception.NameTakenException
import com.homelab.household.domain.exception.NotFoundException
import com.homelab.household.domain.exception.PinLockedException
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.exception.SoleAdminException
import com.homelab.household.domain.exception.UnexpectedContentTypeException
import com.homelab.household.domain.exception.UpstreamGatewayException
import com.homelab.household.domain.exception.WrongPinException
import com.homelab.household.domain.model.Invite
import com.homelab.household.domain.model.ResetCode
import com.homelab.household.domain.model.User
import com.homelab.household.domain.repository.MembersRepository
import com.homelab.household.domain.util.runCatchingSafe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess

class MembersRepositoryImpl(
    private val client: HttpClient,
    private val tokenStorage: TokenStorage,
    private val baseUrl: String
) : MembersRepository {

    override suspend fun listHouseholdMembers(): List<User> = reachingHub {
        client.get("$baseUrl/api/v1/users")
            .ensureJsonSuccess()
            .body<List<UserReadDto>>()
            .map(UserDataMapper::toDomain)
    }

    override suspend fun createInvite(invitedName: String, isAdmin: Boolean): Invite = reachingHub {
        val response = client.post("$baseUrl/api/v1/invites") {
            contentType(ContentType.Application.Json)
            setBody(InviteCreateRequestDto(invitedName = invitedName, isAdmin = isAdmin))
        }
        // The picker tells members apart by name alone, so the hub refuses one already in use.
        if (response.status == HttpStatusCode.Conflict) throw NameTakenException()
        InviteDataMapper.toDomain(response.ensureJsonSuccess().body())
    }

    override suspend fun approvePinReset(memberId: String, ownPin: String): ResetCode = reachingHub {
        val response = client.post("$baseUrl/api/v1/users/$memberId/pin-resets") {
            contentType(ContentType.Application.Json)
            setBody(PinResetApproveRequestDto(pin = ownPin))
        }
        throwOnWrongOrLockedPin(response)
        PinResetDataMapper.toDomain(response.ensureJsonSuccess().body())
    }

    override suspend fun changePin(currentPin: String, newPin: String): Unit = reachingHub {
        val response = client.post("$baseUrl/api/v1/users/me/pin") {
            contentType(ContentType.Application.Json)
            setBody(ChangePinRequestDto(currentPin = currentPin, newPin = newPin))
        }
        throwOnWrongOrLockedPin(response)
        val token: TokenResponseDto = response.ensureJsonSuccess().body()
        tokenStorage.saveTokens(token.access_token)
    }

    override suspend fun removeMember(memberId: String): Unit = reachingHub {
        val response = client.delete("$baseUrl/api/v1/users/$memberId")
        if (response.status == HttpStatusCode.Conflict) throw SoleAdminException()
        response.ensureJsonSuccess()
    }

    override suspend fun leaveHousehold(pin: String): Unit = reachingHub {
        val response = client.delete("$baseUrl/api/v1/users/me") {
            contentType(ContentType.Application.Json)
            setBody(LeaveHouseholdRequestDto(pin = pin))
        }
        throwOnWrongOrLockedPin(response)
        if (response.status == HttpStatusCode.Conflict) throw SoleAdminException()
        response.ensureJsonSuccess()
    }

    /** 403 with `attempts_left` is a wrong PIN, and 429 with `retry_after_seconds` is a lockout, as sign-in reports them. */
    private suspend fun throwOnWrongOrLockedPin(response: HttpResponse) {
        when (response.status) {
            HttpStatusCode.Forbidden -> {
                val attemptsLeft = response.refusal()?.attempts_left
                throw if (attemptsLeft != null) WrongPinException(attemptsLeft) else ForbiddenException("Wrong PIN")
            }
            HttpStatusCode.TooManyRequests -> {
                val seconds = response.refusal()?.retry_after_seconds
                    ?: response.headers[HttpHeaders.RetryAfter]?.toIntOrNull()
                    ?: throw UpstreamGatewayException(statusCode = response.status.value)
                throw PinLockedException(seconds)
            }
        }
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
