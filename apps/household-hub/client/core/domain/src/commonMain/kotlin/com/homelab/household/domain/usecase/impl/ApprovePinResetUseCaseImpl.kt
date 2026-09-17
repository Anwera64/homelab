package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.model.Pin
import com.homelab.household.domain.model.ResetCode
import com.homelab.household.domain.repository.MembersRepository
import com.homelab.household.domain.usecase.ApprovePinResetUseCase

class ApprovePinResetUseCaseImpl(private val membersRepository: MembersRepository) : ApprovePinResetUseCase {
    override suspend operator fun invoke(memberId: String, ownPin: String): ResetCode {
        if (memberId.isBlank()) throw ValidationException("Member ID cannot be blank")
        if (!Pin.isValid(ownPin)) throw ValidationException("A PIN is ${Pin.LENGTH} digits")
        return membersRepository.approvePinReset(memberId, ownPin)
    }
}
