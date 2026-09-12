package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.Member
import com.homelab.household.domain.repository.AuthRepository

/** Everyone who can sign in, for the profile picker. Needs no sign-in itself. */
class ListMembersUseCase(private val authRepository: AuthRepository) {
    suspend operator fun invoke(): List<Member> = authRepository.listMembers()
}
