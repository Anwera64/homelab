package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.model.Space
import com.homelab.household.domain.repository.SpaceRepository
import com.homelab.household.domain.usecase.GetPersonalSpaceUseCase

class GetPersonalSpaceUseCaseImpl(
    private val spaceRepository: SpaceRepository,
) : GetPersonalSpaceUseCase {
    override suspend operator fun invoke(): Space = spaceRepository.getPersonalSpace()
}
