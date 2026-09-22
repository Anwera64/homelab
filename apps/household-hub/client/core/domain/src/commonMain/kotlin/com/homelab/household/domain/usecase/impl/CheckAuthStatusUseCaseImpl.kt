package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.model.AuthStatus
import com.homelab.household.domain.repository.AuthRepository
import com.homelab.household.domain.usecase.CheckAuthStatusUseCase

class CheckAuthStatusUseCaseImpl(
    private val authRepository: AuthRepository,
) : CheckAuthStatusUseCase {
    override suspend operator fun invoke(): AuthStatus = authRepository.checkStatus()
}
