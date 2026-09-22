package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.model.InvitePreview
import com.homelab.household.domain.repository.AuthRepository
import com.homelab.household.domain.usecase.LookUpInviteUseCase

class LookUpInviteUseCaseImpl(
    private val authRepository: AuthRepository,
) : LookUpInviteUseCase {
    override suspend operator fun invoke(code: String): InvitePreview {
        if (code.isBlank()) throw ValidationException("Invite code cannot be blank")
        return authRepository.lookUpInvite(code)
    }
}
