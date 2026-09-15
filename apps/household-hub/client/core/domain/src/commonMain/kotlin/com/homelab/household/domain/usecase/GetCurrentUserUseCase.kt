package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.User

fun interface GetCurrentUserUseCase {
    suspend operator fun invoke(): User?
}
