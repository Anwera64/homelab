package com.homelab.household.data.repository

import com.homelab.household.data.dto.ChangePinRequestDto
import com.homelab.household.data.dto.InviteCreateRequestDto
import com.homelab.household.data.dto.PinResetApproveRequestDto
import com.homelab.household.data.dto.LeaveHouseholdRequestDto
import com.homelab.household.data.dto.TokenResponseDto
import com.homelab.household.data.dto.UserReadDto
import com.homelab.household.data.local.TokenStorage
import com.homelab.household.data.mapper.InviteDataMapper
import com.homelab.household.data.mapper.PinResetDataMapper
import com.homelab.household.data.mapper.UserDataMapper
import com.homelab.household.data.network.ensureJsonSuccess
import com.homelab.household.data.network.reachingHub
import com.homelab.household.data.network.throwIfPinRefused
import com.homelab.household.domain.exception.NameTakenException
import com.homelab.household.domain.exception.SoleAdminException
import com.homelab.household.domain.model.Invite
import com.homelab.household.domain.model.ResetCode
import com.homelab.household.domain.model.User
import com.homelab.household.domain.repository.MembersRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

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
        response.throwIfPinRefused()
        PinResetDataMapper.toDomain(response.ensureJsonSuccess().body())
    }

    override suspend fun changePin(currentPin: String, newPin: String): Unit = reachingHub {
        val response = client.post("$baseUrl/api/v1/users/me/pin") {
            contentType(ContentType.Application.Json)
            setBody(ChangePinRequestDto(currentPin = currentPin, newPin = newPin))
        }
        response.throwIfPinRefused()
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
        response.throwIfPinRefused()
        if (response.status == HttpStatusCode.Conflict) throw SoleAdminException()
        response.ensureJsonSuccess()
    }
}
