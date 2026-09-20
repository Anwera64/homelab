package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.repository.MembersRepository
import com.homelab.household.domain.usecase.RemoveMemberUseCase

class RemoveMemberUseCaseImpl(private val membersRepository: MembersRepository) : RemoveMemberUseCase {
    override suspend operator fun invoke(memberId: String) {
        if (memberId.isBlank()) throw ValidationException("Member ID cannot be blank")
        membersRepository.removeMember(memberId)
    }
}
