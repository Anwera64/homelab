package com.homelab.household.domain.usecase

/** Undo after ✕ on a waiting card: the sentence waits again. */
fun interface RestoreChangeUseCase {
    suspend operator fun invoke(changeId: String)
}
