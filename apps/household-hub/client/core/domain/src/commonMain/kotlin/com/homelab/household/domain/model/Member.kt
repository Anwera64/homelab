package com.homelab.household.domain.model

/** Someone who can sign in, as the profile picker shows them before anyone has. */
data class Member(
    val id: String,
    val name: String,
    val avatarColor: String,
)
