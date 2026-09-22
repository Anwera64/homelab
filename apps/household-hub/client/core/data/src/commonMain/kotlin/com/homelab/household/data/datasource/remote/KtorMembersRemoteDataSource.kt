package com.homelab.household.data.datasource.remote

import com.homelab.household.data.datasource.remote.`interface`.MembersRemoteDataSource
import com.homelab.household.data.dto.ChangePinRequestDto
import com.homelab.household.data.dto.InviteCreateRequestDto
import com.homelab.household.data.dto.InviteReadDto
import com.homelab.household.data.dto.LeaveHouseholdRequestDto
import com.homelab.household.data.dto.PinResetApproveRequestDto
import com.homelab.household.data.dto.PinResetReadDto
import com.homelab.household.data.dto.TokenResponseDto
import com.homelab.household.data.dto.UserReadDto
import com.homelab.household.data.network.ensureJsonSuccess
import com.homelab.household.data.network.reachingHub
import com.homelab.household.data.network.throwIfPinRefused
import com.homelab.household.domain.exception.NameTakenException
import com.homelab.household.domain.exception.SoleAdminException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

class KtorMembersRemoteDataSource(
    private val client: HttpClient,
    private val baseUrl: String,
) : MembersRemoteDataSource {
    override suspend fun listHouseholdMembers(): List<UserReadDto> =
        reachingHub {
            client
                .get("$baseUrl/api/v1/users")
                .ensureJsonSuccess()
                .body<List<UserReadDto>>()
        }

    override suspend fun createInvite(
        invitedName: String,
        isAdmin: Boolean,
    ): InviteReadDto =
        reachingHub {
            val response =
                client.post("$baseUrl/api/v1/invites") {
                    contentType(ContentType.Application.Json)
                    setBody(InviteCreateRequestDto(invitedName = invitedName, isAdmin = isAdmin))
                }
            // The picker tells members apart by name alone, so the hub refuses one already in use.
            if (response.status == HttpStatusCode.Conflict) throw NameTakenException()
            response.ensureJsonSuccess().body()
        }

    override suspend fun approvePinReset(
        memberId: String,
        ownPin: String,
    ): PinResetReadDto =
        reachingHub {
            val response =
                client.post("$baseUrl/api/v1/users/$memberId/pin-resets") {
                    contentType(ContentType.Application.Json)
                    setBody(PinResetApproveRequestDto(pin = ownPin))
                }
            response.throwIfPinRefused()
            response.ensureJsonSuccess().body()
        }

    override suspend fun changePin(
        currentPin: String,
        newPin: String,
    ): TokenResponseDto =
        reachingHub {
            val response =
                client.post("$baseUrl/api/v1/users/me/pin") {
                    contentType(ContentType.Application.Json)
                    setBody(ChangePinRequestDto(currentPin = currentPin, newPin = newPin))
                }
            response.throwIfPinRefused()
            response.ensureJsonSuccess().body()
        }

    override suspend fun removeMember(memberId: String): Unit =
        reachingHub {
            val response = client.delete("$baseUrl/api/v1/users/$memberId")
            if (response.status == HttpStatusCode.Conflict) throw SoleAdminException()
            response.ensureJsonSuccess()
        }

    override suspend fun leaveHousehold(pin: String): Unit =
        reachingHub {
            val response =
                client.delete("$baseUrl/api/v1/users/me") {
                    contentType(ContentType.Application.Json)
                    setBody(LeaveHouseholdRequestDto(pin = pin))
                }
            response.throwIfPinRefused()
            if (response.status == HttpStatusCode.Conflict) throw SoleAdminException()
            response.ensureJsonSuccess()
        }
}
