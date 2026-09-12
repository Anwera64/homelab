package com.homelab.household.domain.usecase

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.model.User
import com.homelab.household.domain.repository.AuthRepository

class LoginUseCase(private val authRepository: AuthRepository) {
    suspend operator fun invoke(username: String, password: String): User {
        if (username.length < 3) {
            throw ValidationException("Username must be at least 3 characters")
        }
        if (password.length < 8) {
            throw ValidationException("Password must be at least 8 characters")
        }
        return authRepository.login(username.trim(), password)
    }
}
