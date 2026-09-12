package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.Space
import com.homelab.household.domain.repository.SpaceRepository

class GetPersonalSpaceUseCase(private val spaceRepository: SpaceRepository) {
    suspend operator fun invoke(): Space = spaceRepository.getPersonalSpace()
}
