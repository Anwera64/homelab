package com.homelab.household.data.dto

import kotlinx.serialization.Serializable

/** Who invited whom, from `GET /invites/{code}`, before the joiner picks a PIN. */
@Serializable
data class InvitePreviewReadDto(
    val invited_name: String,
    val inviter_name: String,
    val inviter_avatar_color: String,
)
