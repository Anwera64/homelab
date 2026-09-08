package com.homelab.household.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserReadDto(
    val id: String,
    val username: String,
    val email: String,
    @SerialName("full_name") val full_name: String,
    @SerialName("is_admin") val is_admin: Boolean,
    @SerialName("is_active") val is_active: Boolean,
    @SerialName("personal_space_id") val personal_space_id: String? = null,
    @SerialName("avatar_color") val avatar_color: String? = "#4F46E5",
    @SerialName("created_at") val created_at: String? = null
)

@Serializable
data class UserLoginRequestDto(
    val username: String,
    val password: String
)

@Serializable
data class UserOnboardRequestDto(
    val username: String,
    val email: String,
    val password: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("avatar_color") val avatarColor: String? = null
)

@Serializable
data class TokenResponseDto(
    @SerialName("access_token") val access_token: String,
    @SerialName("token_type") val token_type: String = "bearer",
    val user: UserReadDto? = null
)

@Serializable
data class AuthStatusDto(
    @SerialName("is_initialized") val is_initialized: Boolean,
    @SerialName("member_count") val member_count: Int
)
