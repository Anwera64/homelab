package com.homelab.household.domain.usecase

import com.homelab.household.domain.repository.AuthRepository

class GetHubHostUseCase(private val authRepository: AuthRepository) {
    operator fun invoke(): String = authRepository.getHubHost()
}
