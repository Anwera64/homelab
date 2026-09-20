package com.homelab.household.data.datasource.remote.`interface`

import com.homelab.household.data.dto.InviteReadDto
import com.homelab.household.data.dto.PinResetReadDto
import com.homelab.household.data.dto.TokenResponseDto
import com.homelab.household.data.dto.UserReadDto

/**
 * Everything the hub is asked about the household's members: who is in it, who may join, and who is
 * leaving. Each function is one call, returning the DTO the hub sent or throwing what its refusal
 * means. `MembersRepositoryImpl` maps those DTOs and decides what this phone keeps.
 */
interface MembersRemoteDataSource {

    suspend fun listHouseholdMembers(): List<UserReadDto>

    suspend fun createInvite(invitedName: String, isAdmin: Boolean): InviteReadDto

    /** [ownPin] is the approver's own PIN, not the PIN of whoever forgot theirs. */
    suspend fun approvePinReset(memberId: String, ownPin: String): PinResetReadDto

    /** A changed PIN invalidates the old token, so the hub hands back a fresh one. */
    suspend fun changePin(currentPin: String, newPin: String): TokenResponseDto

    suspend fun removeMember(memberId: String)

    suspend fun leaveHousehold(pin: String)
}
