package com.homelab.household.domain.usecase

/** A signed-in member changes their own PIN, confirming with the current one. */
fun interface ChangePinUseCase {
    suspend operator fun invoke(
        currentPin: String,
        newPin: String,
    )
}
