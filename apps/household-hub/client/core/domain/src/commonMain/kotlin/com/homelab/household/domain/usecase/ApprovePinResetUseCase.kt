package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.ResetCode

/** One member vouches for another who forgot their PIN, confirming with their own. */
fun interface ApprovePinResetUseCase {
    suspend operator fun invoke(
        memberId: String,
        ownPin: String,
    ): ResetCode
}
