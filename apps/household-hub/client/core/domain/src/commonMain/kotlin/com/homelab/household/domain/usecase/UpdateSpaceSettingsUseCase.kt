package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.Space

fun interface UpdateSpaceSettingsUseCase {
    suspend operator fun invoke(
        spaceId: String,
        settings: Map<String, Any?>,
    ): Space
}
