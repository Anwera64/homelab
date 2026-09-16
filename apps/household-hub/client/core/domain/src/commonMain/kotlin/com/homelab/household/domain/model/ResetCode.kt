package com.homelab.household.domain.model

/** A one-time code a member vouching for someone reads out, so they can pick a new PIN. */
data class ResetCode(
    val code: String,
    val expiresInSeconds: Int
)
