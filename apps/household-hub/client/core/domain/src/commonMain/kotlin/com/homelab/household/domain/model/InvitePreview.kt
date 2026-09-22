package com.homelab.household.domain.model

/** What an invite code looks like before it is redeemed: who invited whom. */
data class InvitePreview(
    val invitedName: String,
    val inviterName: String,
    val inviterAvatarColor: String,
)
