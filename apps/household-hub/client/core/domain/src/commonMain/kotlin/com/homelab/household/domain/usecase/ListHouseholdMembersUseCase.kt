package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.User

/** Every active member of the household, for the members screen. */
fun interface ListHouseholdMembersUseCase {
    suspend operator fun invoke(): List<User>
}
