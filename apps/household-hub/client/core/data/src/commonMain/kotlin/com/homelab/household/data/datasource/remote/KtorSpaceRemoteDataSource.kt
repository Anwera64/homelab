package com.homelab.household.data.datasource.remote

import com.homelab.household.data.datasource.remote.`interface`.SpaceRemoteDataSource
import com.homelab.household.data.dto.SpaceReadDto
import com.homelab.household.data.dto.SpaceUpdateDto
import com.homelab.household.data.network.ensureJsonSuccess
import com.homelab.household.data.network.reachingHub
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class KtorSpaceRemoteDataSource(
    private val client: HttpClient,
    private val baseUrl: String,
) : SpaceRemoteDataSource {
    override suspend fun getPersonalSpace(): SpaceReadDto =
        reachingHub {
            client.get("$baseUrl/api/v1/spaces/personal").ensureJsonSuccess().body()
        }

    override suspend fun getHouseholdSpace(): SpaceReadDto =
        reachingHub {
            client.get("$baseUrl/api/v1/spaces/shared").ensureJsonSuccess().body()
        }

    override suspend fun updateSpaceSettings(
        spaceId: String,
        settings: Map<String, String>,
    ): SpaceReadDto =
        reachingHub {
            client
                .put("$baseUrl/api/v1/spaces/$spaceId/settings") {
                    contentType(ContentType.Application.Json)
                    setBody(SpaceUpdateDto(settings = settings))
                }.ensureJsonSuccess()
                .body()
        }
}
