package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.ServerStatus
import kotlinx.coroutines.flow.Flow

fun interface ObserveServerStatusUseCase {
    operator fun invoke(): Flow<ServerStatus>
}
