package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.User

/** A member picked from the profile list signs in with their PIN. */
fun interface LoginUseCase {
    suspend operator fun invoke(memberId: String, pin: String): User
}
