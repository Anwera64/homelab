package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.User
import com.homelab.household.domain.repository.AuthRepository

class GetCurrentUserUseCase(private val authRepository: AuthRepository) {
    suspend operator fun invoke(): User? = authRepository.getCurrentUser()
}
