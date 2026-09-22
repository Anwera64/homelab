package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.User

/** First run: the first member, and the hub's admin, with a name, a PIN and a colour. */
fun interface FirstRunOnboardUseCase {
    suspend operator fun invoke(
        name: String,
        pin: String,
        avatarColor: String,
    ): User
}
