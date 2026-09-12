package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.User
import com.homelab.household.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow

class ObserveCurrentUserUseCase(private val authRepository: AuthRepository) {
    operator fun invoke(): Flow<User?> = authRepository.observeCurrentUser()
}
