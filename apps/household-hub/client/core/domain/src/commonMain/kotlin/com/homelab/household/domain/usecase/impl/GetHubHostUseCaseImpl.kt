package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.repository.AuthRepository
import com.homelab.household.domain.usecase.GetHubHostUseCase

class GetHubHostUseCaseImpl(
    private val authRepository: AuthRepository,
) : GetHubHostUseCase {
    override operator fun invoke(): String = authRepository.getHubHost()
}
