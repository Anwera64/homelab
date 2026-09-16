package com.homelab.household.presentation.invitecreate

import com.homelab.household.domain.model.Invite
import com.homelab.household.presentation.firstrun.NameError

/** Inviting someone: their name, whether they join as an admin, and the code once it exists. */
data class InviteCreateUiState(
    val name: String = "",
    val isAdmin: Boolean = false,
    val nameError: NameError? = null,
    val invite: Invite? = null,
    val secondsLeft: Int = 0,
    val status: InviteCreateStatus = InviteCreateStatus.Idle
)
