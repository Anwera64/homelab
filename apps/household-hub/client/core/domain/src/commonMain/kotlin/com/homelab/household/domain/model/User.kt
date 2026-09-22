package com.homelab.household.domain.model

data class User(
    val id: String,
    val fullName: String,
    val isAdmin: Boolean,
    val isActive: Boolean,
    val personalSpaceId: String? = null,
    val avatarColor: String? = null,
    val createdAt: String? = null,
)
