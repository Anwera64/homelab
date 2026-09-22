package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.model.Pin
import com.homelab.household.domain.model.User
import com.homelab.household.domain.repository.AuthRepository
import com.homelab.household.domain.usecase.LoginUseCase

class LoginUseCaseImpl(
    private val authRepository: AuthRepository,
) : LoginUseCase {
    override suspend operator fun invoke(
        memberId: String,
        pin: String,
    ): User {
        if (!Pin.isValid(pin)) {
            throw ValidationException("A PIN is ${Pin.LENGTH} digits")
        }
        return authRepository.login(memberId, pin)
    }
}
