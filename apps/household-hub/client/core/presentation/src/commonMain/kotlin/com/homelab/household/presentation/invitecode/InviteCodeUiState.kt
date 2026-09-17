package com.homelab.household.presentation.invitecode

/** The invite-code screen: the characters typed so far, and how the hub answered about them. */
data class InviteCodeUiState(
    val code: String = "",
    val status: InviteCodeStatus = InviteCodeStatus.Idle
)
