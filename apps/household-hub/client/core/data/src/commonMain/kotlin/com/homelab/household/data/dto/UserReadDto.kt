package com.homelab.household.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserReadDto(
    val id: String,
    @SerialName("full_name") val full_name: String,
    @SerialName("is_admin") val is_admin: Boolean,
    @SerialName("is_active") val is_active: Boolean,
    @SerialName("personal_space_id") val personal_space_id: String? = null,
    @SerialName("avatar_color") val avatar_color: String? = null,
    @SerialName("created_at") val created_at: String? = null
)
