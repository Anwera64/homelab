package com.homelab.household.data.datasource.remote

import com.homelab.household.data.datasource.remote.`interface`.ToolApprovalRemoteDataSource
import com.homelab.household.data.dto.ToolApprovalDto
import com.homelab.household.data.dto.ToolApprovalUpdateDto
import com.homelab.household.data.network.ensureJsonSuccess
import com.homelab.household.data.network.reachingHub
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class KtorToolApprovalRemoteDataSource(
    private val client: HttpClient,
    private val baseUrl: String,
) : ToolApprovalRemoteDataSource {
    override suspend fun getToolApprovals(): List<ToolApprovalDto> =
        reachingHub {
            client.get("$baseUrl$PATH").ensureJsonSuccess().body()
        }

    override suspend fun setToolApproval(update: ToolApprovalUpdateDto): List<ToolApprovalDto> =
        reachingHub {
            client
                .put("$baseUrl$PATH") {
                    contentType(ContentType.Application.Json)
                    setBody(update)
                }.ensureJsonSuccess()
                .body()
        }

    private companion object {
        const val PATH = "/api/v1/users/me/tool-approvals"
    }
}
