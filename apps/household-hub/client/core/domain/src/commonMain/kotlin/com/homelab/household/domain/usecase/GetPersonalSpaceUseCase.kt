package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.Space

fun interface GetPersonalSpaceUseCase {
    suspend operator fun invoke(): Space
}
