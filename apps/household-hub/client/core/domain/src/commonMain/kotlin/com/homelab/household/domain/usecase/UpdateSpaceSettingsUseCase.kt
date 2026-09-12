package com.homelab.household.domain.usecase

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.model.Space
import com.homelab.household.domain.repository.SpaceRepository

class UpdateSpaceSettingsUseCase(private val spaceRepository: SpaceRepository) {
    suspend operator fun invoke(spaceId: String, settings: Map<String, Any?>): Space {
        if (spaceId.isBlank()) throw ValidationException("Space ID cannot be blank")
        return spaceRepository.updateSpaceSettings(spaceId, settings)
    }
}
