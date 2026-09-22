package com.homelab.household.data.repository

import com.homelab.household.data.datasource.local.StoredSessionLocalDataSource
import com.homelab.household.data.datasource.remote.`interface`.MembersRemoteDataSource
import com.homelab.household.data.mapper.InviteDataMapper
import com.homelab.household.data.mapper.PinResetDataMapper
import com.homelab.household.data.mapper.UserDataMapper
import com.homelab.household.domain.model.Invite
import com.homelab.household.domain.model.ResetCode
import com.homelab.household.domain.model.User
import com.homelab.household.domain.repository.MembersRepository

/**
 * Orchestration and mapping for the household's members. The only decision it makes of its own is
 * the one in [changePin]: a changed PIN invalidates the old token, so the fresh one has to replace
 * what is kept on this phone before anything else uses it.
 */
class MembersRepositoryImpl(
    private val remote: MembersRemoteDataSource,
    private val storage: StoredSessionLocalDataSource,
) : MembersRepository {
    override suspend fun listHouseholdMembers(): List<User> =
        remote.listHouseholdMembers().map(UserDataMapper::toDomain)

    override suspend fun createInvite(
        invitedName: String,
        isAdmin: Boolean,
    ): Invite = InviteDataMapper.toDomain(remote.createInvite(invitedName, isAdmin))

    override suspend fun approvePinReset(
        memberId: String,
        ownPin: String,
    ): ResetCode = PinResetDataMapper.toDomain(remote.approvePinReset(memberId, ownPin))

    override suspend fun changePin(
        currentPin: String,
        newPin: String,
    ) {
        storage.saveTokens(remote.changePin(currentPin, newPin).access_token)
    }

    override suspend fun removeMember(memberId: String) = remote.removeMember(memberId)

    override suspend fun leaveHousehold(pin: String) = remote.leaveHousehold(pin)
}
