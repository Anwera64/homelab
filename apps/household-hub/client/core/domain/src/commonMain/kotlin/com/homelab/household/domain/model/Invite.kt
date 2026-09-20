package com.homelab.household.domain.model

/** A one-time code an admin hands out; whoever redeems it joins as [invitedName]. */
data class Invite(
    val code: String,
    val invitedName: String,
    val isAdmin: Boolean,
    val expiresInSeconds: Int
)
