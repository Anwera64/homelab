package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.model.User
import com.homelab.household.domain.repository.MembersRepository
import com.homelab.household.domain.usecase.ListHouseholdMembersUseCase

class ListHouseholdMembersUseCaseImpl(
    private val membersRepository: MembersRepository,
) : ListHouseholdMembersUseCase {
    override suspend operator fun invoke(): List<User> = membersRepository.listHouseholdMembers()
}
