package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.AuthStatus
import com.homelab.household.domain.repository.AuthRepository

class CheckAuthStatusUseCase(private val authRepository: AuthRepository) {
    suspend operator fun invoke(): AuthStatus = authRepository.checkStatus()
}
