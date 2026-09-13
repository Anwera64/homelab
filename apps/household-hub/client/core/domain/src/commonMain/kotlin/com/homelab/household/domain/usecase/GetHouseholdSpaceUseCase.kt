package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.Space

fun interface GetHouseholdSpaceUseCase {
    suspend operator fun invoke(): Space
}
