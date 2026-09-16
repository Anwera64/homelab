package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.User

/** The joiner redeems their invite code with the name, PIN and colour they chose, and is signed in. */
fun interface JoinHouseholdUseCase {
    suspend operator fun invoke(code: String, fullName: String, pin: String, avatarColor: String): User
}
