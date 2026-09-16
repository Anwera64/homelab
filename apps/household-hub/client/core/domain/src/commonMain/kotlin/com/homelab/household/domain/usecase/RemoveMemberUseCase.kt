package com.homelab.household.domain.usecase

/** An admin removes someone else from the household. */
fun interface RemoveMemberUseCase {
    suspend operator fun invoke(memberId: String)
}
