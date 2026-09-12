package com.homelab.household.domain.model

data class AuthToken(
    val accessToken: String,
    val tokenType: String = "bearer",
    val user: User
)
