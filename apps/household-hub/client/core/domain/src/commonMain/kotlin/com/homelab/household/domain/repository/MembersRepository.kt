package com.homelab.household.domain.repository

import com.homelab.household.domain.model.Invite
import com.homelab.household.domain.model.ResetCode
import com.homelab.household.domain.model.User

interface MembersRepository {
    suspend fun listHouseholdMembers(): List<User>
    suspend fun createInvite(invitedName: String, isAdmin: Boolean): Invite
    suspend fun approvePinReset(memberId: String, ownPin: String): ResetCode

    /** Keeps the new token, as [AuthRepository]'s sign-in does. */
    suspend fun changePin(currentPin: String, newPin: String)
    suspend fun removeMember(memberId: String)
    suspend fun leaveHousehold(pin: String)
}
