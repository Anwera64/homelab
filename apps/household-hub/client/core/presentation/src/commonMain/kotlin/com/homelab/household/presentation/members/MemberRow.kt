package com.homelab.household.presentation.members

/** One row of the members list: who they are, and what the screen may offer for them. */
data class MemberRow(
    val id: String,
    val name: String,
    val avatarColor: String,
    val isYou: Boolean,
    val isAdmin: Boolean,
)
