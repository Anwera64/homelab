package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.model.User
import com.homelab.household.domain.repository.AuthRepository
import com.homelab.household.domain.usecase.ObserveCurrentUserUseCase
import kotlinx.coroutines.flow.Flow

class ObserveCurrentUserUseCaseImpl(private val authRepository: AuthRepository) : ObserveCurrentUserUseCase {
    override operator fun invoke(): Flow<User?> = authRepository.observeCurrentUser()
}
