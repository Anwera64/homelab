package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.Invite

/** An admin invites someone by name and gets back a one-time code for them to redeem. */
fun interface CreateInviteUseCase {
    suspend operator fun invoke(invitedName: String, isAdmin: Boolean): Invite
}
