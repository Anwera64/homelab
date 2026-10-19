package com.homelab.household.domain.usecase

/** ✕ on a waiting card: take back a sentence you asked to remember, before it was answered. */
fun interface WithdrawChangeUseCase {
    suspend operator fun invoke(changeId: String)
}
