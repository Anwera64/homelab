package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.model.Pin
import com.homelab.household.domain.model.User
import com.homelab.household.domain.repository.AuthRepository
import com.homelab.household.domain.usecase.RedeemPinResetUseCase

class RedeemPinResetUseCaseImpl(private val authRepository: AuthRepository) : RedeemPinResetUseCase {
    override suspend operator fun invoke(code: String, pin: String): User {
        if (code.isBlank()) throw ValidationException("Reset code cannot be blank")
        if (!Pin.isValid(pin)) throw ValidationException("A PIN is ${Pin.LENGTH} digits")
        return authRepository.redeemPinReset(code, pin)
    }
}
