package com.homelab.household.data.repository

import com.homelab.household.data.dto.SpaceReadDto
import com.homelab.household.data.dto.SpaceUpdateDto
import com.homelab.household.data.mapper.SpaceDataMapper
import com.homelab.household.data.network.NetworkExceptionHelper
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.Space
import com.homelab.household.domain.repository.SpaceRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class SpaceRepositoryImpl(
    private val client: HttpClient,
    private val baseUrl: String
) : SpaceRepository {

    override suspend fun getPersonalSpace(): Space {
        return try {
            val dto = client.get("$baseUrl/api/v1/spaces/personal").body<SpaceReadDto>()
            SpaceDataMapper.toDomain(dto)
        } catch (e: Exception) {
            handleOfflineOrThrow(e)
        }
    }

    override suspend fun getHouseholdSpace(): Space {
        return try {
            val dto = client.get("$baseUrl/api/v1/spaces/shared").body<SpaceReadDto>()
            SpaceDataMapper.toDomain(dto)
        } catch (e: Exception) {
            handleOfflineOrThrow(e)
        }
    }

    override suspend fun updateSpaceSettings(
        spaceId: String,
        settings: Map<String, Any?>
    ): Space {
        return try {
            val stringSettings = settings.mapValues { it.value?.toString() ?: "" }
            val dto = client.put("$baseUrl/api/v1/spaces/$spaceId/settings") {
                contentType(ContentType.Application.Json)
                setBody(SpaceUpdateDto(settings = stringSettings))
            }.body<SpaceReadDto>()
            SpaceDataMapper.toDomain(dto)
        } catch (e: Exception) {
            handleOfflineOrThrow(e)
        }
    }

    private fun <T> handleOfflineOrThrow(e: Throwable): T {
        if (NetworkExceptionHelper.isNetworkOfflineException(e)) {
            throw ServerOfflineException(message = e.message ?: "Server is offline", cause = e)
        }
        throw e
    }
}
