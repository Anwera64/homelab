package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.model.Pin
import com.homelab.household.domain.repository.MembersRepository
import com.homelab.household.domain.usecase.LeaveHouseholdUseCase

class LeaveHouseholdUseCaseImpl(private val membersRepository: MembersRepository) : LeaveHouseholdUseCase {
    override suspend operator fun invoke(pin: String) {
        if (!Pin.isValid(pin)) throw ValidationException("A PIN is ${Pin.LENGTH} digits")
        membersRepository.leaveHousehold(pin)
    }
}
