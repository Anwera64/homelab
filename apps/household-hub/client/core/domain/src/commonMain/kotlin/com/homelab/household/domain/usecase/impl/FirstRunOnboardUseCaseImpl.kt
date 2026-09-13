package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.model.MemberName
import com.homelab.household.domain.model.Pin
import com.homelab.household.domain.model.User
import com.homelab.household.domain.repository.AuthRepository
import com.homelab.household.domain.usecase.FirstRunOnboardUseCase

class FirstRunOnboardUseCaseImpl(private val authRepository: AuthRepository) : FirstRunOnboardUseCase {
    override suspend operator fun invoke(name: String, pin: String, avatarColor: String): User {
        if (!MemberName.isValid(name)) throw ValidationException("A name is 1 to ${MemberName.MAX_LENGTH} characters")
        if (!Pin.isValid(pin)) throw ValidationException("A PIN is ${Pin.LENGTH} digits")
        return authRepository.onboard(name.trim(), pin, avatarColor)
    }
}
