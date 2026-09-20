package com.homelab.household.data.datasource.remote

import com.homelab.household.data.dto.GossipMilestoneDto
import com.homelab.household.data.network.ensureJsonSuccess
import com.homelab.household.data.network.reachingHub
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter

class KtorGossipRemoteDataSource(
    private val client: HttpClient,
    private val baseUrl: String,
) : GossipRemoteDataSource {

    override suspend fun listHouseholdMilestones(limit: Int): List<GossipMilestoneDto> = reachingHub {
        client.get("$baseUrl/api/v1/gossip/household") {
            parameter("limit", limit)
        }.ensureJsonSuccess().body()
    }

    override suspend fun listUserAuditMilestones(limit: Int): List<GossipMilestoneDto> = reachingHub {
        client.get("$baseUrl/api/v1/gossip/audit") {
            parameter("limit", limit)
        }.ensureJsonSuccess().body()
    }

    override suspend fun revokeMilestone(milestoneId: String): Unit = reachingHub {
        client.delete("$baseUrl/api/v1/gossip/$milestoneId").ensureJsonSuccess()
    }
}
