package com.homelab.household.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One face on the profile picker, from `GET /auth/members`. */
@Serializable
data class MemberProfileDto(
    val id: String,
    @SerialName("full_name") val full_name: String,
    @SerialName("avatar_color") val avatar_color: String
)
