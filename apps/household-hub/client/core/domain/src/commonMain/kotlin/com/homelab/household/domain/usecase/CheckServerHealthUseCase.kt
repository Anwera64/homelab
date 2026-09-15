package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.ServerStatus

fun interface CheckServerHealthUseCase {
    suspend operator fun invoke(): ServerStatus
}
