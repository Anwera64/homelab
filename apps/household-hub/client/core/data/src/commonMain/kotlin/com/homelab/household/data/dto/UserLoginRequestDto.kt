package com.homelab.household.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class UserLoginRequestDto(
    val username: String,
    val password: String
)
