package com.homelab.household.domain.usecase

fun interface LogoutUseCase {
    suspend operator fun invoke()
}
