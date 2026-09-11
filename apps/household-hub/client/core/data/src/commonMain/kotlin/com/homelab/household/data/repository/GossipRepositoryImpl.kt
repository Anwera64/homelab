package com.homelab.household.data.repository

import com.homelab.household.data.dto.GossipMilestoneDto
import com.homelab.household.data.mapper.GossipDataMapper
import com.homelab.household.data.remote.NetworkExceptionHelper
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.HouseholdMilestone
import com.homelab.household.domain.repository.GossipRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter

class GossipRepositoryImpl(
    private val client: HttpClient,
    private val baseUrl: String
) : GossipRepository {

    override suspend fun listHouseholdMilestones(limit: Int): List<HouseholdMilestone> {
        return try {
            val dtoList = client.get("$baseUrl/api/v1/gossip/household") {
                parameter("limit", limit)
            }.body<List<GossipMilestoneDto>>()
            dtoList.map { GossipDataMapper.toDomain(it) }
        } catch (e: Exception) {
            handleOfflineOrThrow(e)
        }
    }

    override suspend fun listUserAuditMilestones(limit: Int): List<HouseholdMilestone> {
        return try {
            val dtoList = client.get("$baseUrl/api/v1/gossip/audit") {
                parameter("limit", limit)
            }.body<List<GossipMilestoneDto>>()
            dtoList.map { GossipDataMapper.toDomain(it) }
        } catch (e: Exception) {
            handleOfflineOrThrow(e)
        }
    }

    override suspend fun revokeMilestone(milestoneId: String) {
        try {
            client.delete("$baseUrl/api/v1/gossip/$milestoneId")
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
