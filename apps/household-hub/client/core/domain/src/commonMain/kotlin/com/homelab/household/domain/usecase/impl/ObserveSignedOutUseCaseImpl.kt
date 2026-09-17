package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.repository.AuthRepository
import com.homelab.household.domain.usecase.ObserveSignedOutUseCase
import kotlinx.coroutines.flow.Flow

class ObserveSignedOutUseCaseImpl(private val authRepository: AuthRepository) : ObserveSignedOutUseCase {
    override operator fun invoke(): Flow<Unit> = authRepository.observeSignedOut()
}
