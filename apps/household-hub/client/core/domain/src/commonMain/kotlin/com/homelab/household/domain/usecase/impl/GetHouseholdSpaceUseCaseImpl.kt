package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.model.Space
import com.homelab.household.domain.repository.SpaceRepository
import com.homelab.household.domain.usecase.GetHouseholdSpaceUseCase

class GetHouseholdSpaceUseCaseImpl(private val spaceRepository: SpaceRepository) : GetHouseholdSpaceUseCase {
    override suspend operator fun invoke(): Space = spaceRepository.getHouseholdSpace()
}
