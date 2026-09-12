package com.homelab.household.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TokenResponseDto(
    @SerialName("access_token") val access_token: String,
    @SerialName("token_type") val token_type: String = "bearer",
    val user: UserReadDto? = null
)
