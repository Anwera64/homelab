package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.User

/** Whoever forgot their PIN redeems the code they were given, chooses a new PIN and is signed in. */
fun interface RedeemPinResetUseCase {
    suspend operator fun invoke(code: String, pin: String): User
}
