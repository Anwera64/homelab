package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.model.Member
import com.homelab.household.domain.repository.AuthRepository
import com.homelab.household.domain.usecase.ListMembersUseCase

class ListMembersUseCaseImpl(
    private val authRepository: AuthRepository,
) : ListMembersUseCase {
    override suspend operator fun invoke(): List<Member> = authRepository.listMembers()
}
