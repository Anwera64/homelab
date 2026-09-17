package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.model.Invite
import com.homelab.household.domain.model.MemberName
import com.homelab.household.domain.repository.MembersRepository
import com.homelab.household.domain.usecase.CreateInviteUseCase

class CreateInviteUseCaseImpl(private val membersRepository: MembersRepository) : CreateInviteUseCase {
    override suspend operator fun invoke(invitedName: String, isAdmin: Boolean): Invite {
        if (!MemberName.isValid(invitedName)) throw ValidationException("A name is 1 to ${MemberName.MAX_LENGTH} characters")
        return membersRepository.createInvite(invitedName.trim(), isAdmin)
    }
}
