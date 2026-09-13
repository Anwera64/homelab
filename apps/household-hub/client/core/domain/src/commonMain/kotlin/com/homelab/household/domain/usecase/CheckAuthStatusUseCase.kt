package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.AuthStatus

fun interface CheckAuthStatusUseCase {
    suspend operator fun invoke(): AuthStatus
}
