package com.homelab.household.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class InviteReadDto(
    val code: String,
    val invited_name: String,
    val is_admin: Boolean,
    val expires_in_seconds: Int,
)
