package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.model.MemberName
import com.homelab.household.domain.model.Pin
import com.homelab.household.domain.model.User
import com.homelab.household.domain.repository.AuthRepository
import com.homelab.household.domain.usecase.JoinHouseholdUseCase

class JoinHouseholdUseCaseImpl(private val authRepository: AuthRepository) : JoinHouseholdUseCase {
    override suspend operator fun invoke(code: String, fullName: String, pin: String, avatarColor: String): User {
        if (code.isBlank()) throw ValidationException("Invite code cannot be blank")
        if (!MemberName.isValid(fullName)) throw ValidationException("A name is 1 to ${MemberName.MAX_LENGTH} characters")
        if (!Pin.isValid(pin)) throw ValidationException("A PIN is ${Pin.LENGTH} digits")
        return authRepository.joinHousehold(code, fullName.trim(), pin, avatarColor)
    }
}
