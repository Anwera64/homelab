package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.repository.AuthRepository
import com.homelab.household.domain.usecase.RenewSessionUseCase
import com.homelab.household.domain.util.runCatchingSafe

class RenewSessionUseCaseImpl(private val authRepository: AuthRepository) : RenewSessionUseCase {
    override suspend operator fun invoke() {
        runCatchingSafe { authRepository.refreshToken() }
    }
}
