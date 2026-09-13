package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.Member

/** Everyone who can sign in, for the profile picker. Needs no sign-in itself. */
fun interface ListMembersUseCase {
    suspend operator fun invoke(): List<Member>
}
