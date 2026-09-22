package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.repository.AuthRepository
import com.homelab.household.domain.usecase.HasStoredSessionUseCase

class HasStoredSessionUseCaseImpl(
    private val authRepository: AuthRepository,
) : HasStoredSessionUseCase {
    override operator fun invoke(): Boolean = authRepository.hasStoredSession()
}
