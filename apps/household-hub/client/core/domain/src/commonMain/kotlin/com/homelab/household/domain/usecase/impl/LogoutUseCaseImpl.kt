package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.repository.AuthRepository
import com.homelab.household.domain.usecase.LogoutUseCase

class LogoutUseCaseImpl(private val authRepository: AuthRepository) : LogoutUseCase {
    override suspend operator fun invoke() = authRepository.logout()
}
