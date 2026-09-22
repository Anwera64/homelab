package com.homelab.household.presentation.removemember

import com.homelab.household.domain.model.Member

/** Removing someone: their name typed out, because the damage deserves the friction. */
data class RemoveMemberUiState(
    val member: Member,
    val typedName: String = "",
    val nameMismatch: Boolean = false,
    val status: RemoveMemberStatus = RemoveMemberStatus.Idle,
)
