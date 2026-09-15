package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.User
import kotlinx.coroutines.flow.Flow

fun interface ObserveCurrentUserUseCase {
    operator fun invoke(): Flow<User?>
}
