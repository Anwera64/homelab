package com.homelab.household.data.datasource.remote.`interface`

import com.homelab.household.data.dto.SpaceReadDto

/**
 * Everything the hub is asked about the household's two spaces — the one nobody else can see, and
 * the one everybody shares. Each function is one call, returning the DTO the hub sent.
 * `SpaceRepositoryImpl` maps those DTOs and turns a settings change into the wire's string map.
 */
interface SpaceRemoteDataSource {
    suspend fun getPersonalSpace(): SpaceReadDto

    suspend fun getHouseholdSpace(): SpaceReadDto

    /** [settings] is already the wire shape — the repository has turned every value into a string. */
    suspend fun updateSpaceSettings(
        spaceId: String,
        settings: Map<String, String>,
    ): SpaceReadDto
}
