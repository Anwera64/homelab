package com.homelab.household.presentation.profile

import com.homelab.household.domain.model.User

/** Your own account: who you are, and whether the household could manage without you. */
data class ProfileUiState(
    val member: User? = null,
    val isSoleAdmin: Boolean = false,
    val status: ProfileStatus = ProfileStatus.Loading
)
