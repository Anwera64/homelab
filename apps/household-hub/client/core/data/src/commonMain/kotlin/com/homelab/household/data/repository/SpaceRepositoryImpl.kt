package com.homelab.household.data.repository

import com.homelab.household.data.datasource.remote.SpaceRemoteDataSource
import com.homelab.household.data.mapper.SpaceDataMapper
import com.homelab.household.domain.model.Space
import com.homelab.household.domain.repository.SpaceRepository

/**
 * Orchestration and mapping for the household's two spaces. [remote] speaks DTOs; every DTO becomes
 * a `Space` here, and a settings change is turned into the wire's string map before it goes out.
 */
class SpaceRepositoryImpl(
    private val remote: SpaceRemoteDataSource,
) : SpaceRepository {

    override suspend fun getPersonalSpace(): Space =
        SpaceDataMapper.toDomain(remote.getPersonalSpace())

    override suspend fun getHouseholdSpace(): Space =
        SpaceDataMapper.toDomain(remote.getHouseholdSpace())

    override suspend fun updateSpaceSettings(spaceId: String, settings: Map<String, Any?>): Space =
        SpaceDataMapper.toDomain(remote.updateSpaceSettings(spaceId, SpaceDataMapper.toWireSettings(settings)))
}
