package com.homelab.household.domain.model

data class User(
    val id: String,
    val username: String,
    val email: String,
    val fullName: String,
    val isAdmin: Boolean,
    val isActive: Boolean,
    val personalSpaceId: String? = null,
    val avatarColor: String? = "#4F46E5",
    val createdAt: String? = null
)

data class AuthStatus(
    val isInitialized: Boolean,
    val memberCount: Int
)

data class AuthToken(
    val accessToken: String,
    val tokenType: String = "bearer",
    val user: User
)
