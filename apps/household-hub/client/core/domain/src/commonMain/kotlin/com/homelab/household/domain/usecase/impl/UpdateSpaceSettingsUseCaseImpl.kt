package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.model.Space
import com.homelab.household.domain.repository.SpaceRepository
import com.homelab.household.domain.usecase.UpdateSpaceSettingsUseCase

class UpdateSpaceSettingsUseCaseImpl(
    private val spaceRepository: SpaceRepository,
) : UpdateSpaceSettingsUseCase {
    override suspend operator fun invoke(
        spaceId: String,
        settings: Map<String, Any?>,
    ): Space {
        if (spaceId.isBlank()) throw ValidationException("Space ID cannot be blank")
        return spaceRepository.updateSpaceSettings(spaceId, settings)
    }
}
