package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.model.User
import com.homelab.household.domain.repository.AuthRepository
import com.homelab.household.domain.usecase.GetCurrentUserUseCase

class GetCurrentUserUseCaseImpl(private val authRepository: AuthRepository) : GetCurrentUserUseCase {
    override suspend operator fun invoke(): User? = authRepository.getCurrentUser()
}
