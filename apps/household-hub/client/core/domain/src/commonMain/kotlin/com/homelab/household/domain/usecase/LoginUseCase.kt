package com.homelab.household.domain.usecase

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.model.Pin
import com.homelab.household.domain.model.User
import com.homelab.household.domain.repository.AuthRepository

/** A member picked from the profile list signs in with their PIN. */
class LoginUseCase(private val authRepository: AuthRepository) {
    suspend operator fun invoke(memberId: String, pin: String): User {
        if (!Pin.isValid(pin)) {
            throw ValidationException("A PIN is ${Pin.LENGTH} digits")
        }
        return authRepository.login(memberId, pin)
    }
}
