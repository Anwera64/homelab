package com.homelab.household.domain.repository

import com.homelab.household.domain.model.Space

interface SpaceRepository {
    suspend fun getPersonalSpace(): Space

    suspend fun getHouseholdSpace(): Space

    suspend fun updateSpaceSettings(
        spaceId: String,
        settings: Map<String, Any?>,
    ): Space
}
