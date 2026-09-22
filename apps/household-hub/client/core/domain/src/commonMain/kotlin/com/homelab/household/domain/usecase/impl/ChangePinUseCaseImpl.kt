package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.model.Pin
import com.homelab.household.domain.repository.MembersRepository
import com.homelab.household.domain.usecase.ChangePinUseCase

class ChangePinUseCaseImpl(
    private val membersRepository: MembersRepository,
) : ChangePinUseCase {
    override suspend operator fun invoke(
        currentPin: String,
        newPin: String,
    ) {
        if (!Pin.isValid(currentPin)) throw ValidationException("A PIN is ${Pin.LENGTH} digits")
        if (!Pin.isValid(newPin)) throw ValidationException("A PIN is ${Pin.LENGTH} digits")
        membersRepository.changePin(currentPin, newPin)
    }
}
