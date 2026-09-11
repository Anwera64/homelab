package com.homelab.household.domain.usecase

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.model.AuthStatus
import com.homelab.household.domain.model.User
import com.homelab.household.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow

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

class FirstRunOnboardUseCase(private val authRepository: AuthRepository) {
    suspend operator fun invoke(
        username: String,
        email: String,
        password: String,
        fullName: String,
        avatarColor: String? = null
    ): User {
        if (username.length < 3) throw ValidationException("Username must be at least 3 characters")
        if (!email.contains("@")) throw ValidationException("Invalid email format")
        if (password.length < 8) throw ValidationException("Password must be at least 8 characters")
        if (fullName.isBlank()) throw ValidationException("Full name cannot be blank")
        return authRepository.onboard(username.trim(), email.trim(), password, fullName.trim(), avatarColor)
    }
}

class CheckAuthStatusUseCase(private val authRepository: AuthRepository) {
    suspend operator fun invoke(): AuthStatus = authRepository.checkStatus()
}

class GetCurrentUserUseCase(private val authRepository: AuthRepository) {
    suspend operator fun invoke(): User? = authRepository.getCurrentUser()
}

class LogoutUseCase(private val authRepository: AuthRepository) {
    suspend operator fun invoke() = authRepository.logout()
}

class ObserveCurrentUserUseCase(private val authRepository: AuthRepository) {
    operator fun invoke(): Flow<User?> = authRepository.observeCurrentUser()
}

class GetHubHostUseCase(private val authRepository: AuthRepository) {
    operator fun invoke(): String = authRepository.getHubHost()
}
