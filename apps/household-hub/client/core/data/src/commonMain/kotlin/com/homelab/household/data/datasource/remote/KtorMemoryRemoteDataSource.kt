package com.homelab.household.data.datasource.remote

import com.homelab.household.data.datasource.remote.`interface`.MemoryRemoteDataSource
import com.homelab.household.data.dto.ChangeAnswerDto
import com.homelab.household.data.dto.ChangesToCheckDto
import com.homelab.household.data.dto.LookedOverDto
import com.homelab.household.data.dto.MemoryPageDto
import com.homelab.household.data.network.ensureJsonSuccess
import com.homelab.household.data.network.pinRefusal
import com.homelab.household.data.network.reachingHub
import com.homelab.household.domain.exception.ChangeWithdrawnException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

class KtorMemoryRemoteDataSource(
    private val client: HttpClient,
    private val baseUrl: String,
) : MemoryRemoteDataSource {
    override suspend fun getMemoryPage(): MemoryPageDto =
        reachingHub {
            client.get("$baseUrl/api/v1/memories/page").ensureJsonSuccess().body()
        }

    override suspend fun removeMemory(memoryId: String): Unit =
        reachingHub {
            client.delete("$baseUrl/api/v1/memories/$memoryId").ensureJsonSuccess()
        }

    override suspend fun restoreMemory(memoryId: String): Unit =
        reachingHub {
            client.post("$baseUrl/api/v1/memories/$memoryId/restore").ensureJsonSuccess()
        }

    override suspend fun getChangesToCheck(): ChangesToCheckDto =
        reachingHub {
            client.get("$baseUrl/api/v1/memories/proposals").ensureJsonSuccess().body()
        }

    override suspend fun answerChange(
        changeId: String,
        answer: String,
    ): Unit =
        reachingHub {
            val response =
                client.post("$baseUrl/api/v1/memories/proposals/$changeId/answer") {
                    contentType(ContentType.Application.Json)
                    setBody(ChangeAnswerDto(answer = answer))
                }
            // Its asker took it back while it waited: the hub says who, and the card says so.
            if (response.status == HttpStatusCode.Conflict) {
                val refusal = response.pinRefusal()
                if (refusal?.code == CHANGE_WITHDRAWN) throw ChangeWithdrawnException(refusal.detail.orEmpty())
            }
            response.ensureJsonSuccess()
        }

    override suspend fun undoChangeAnswer(changeId: String): Unit =
        reachingHub {
            client.post("$baseUrl/api/v1/memories/proposals/$changeId/undo").ensureJsonSuccess()
        }

    override suspend fun getLookedOver(sessionId: String): LookedOverDto =
        reachingHub {
            client.get("$baseUrl/api/v1/sessions/$sessionId/looked-over").ensureJsonSuccess().body()
        }

    override suspend fun withdrawChange(changeId: String): Unit =
        reachingHub {
            client.delete("$baseUrl/api/v1/memories/proposals/$changeId").ensureJsonSuccess()
        }

    override suspend fun restoreChange(changeId: String): Unit =
        reachingHub {
            client.post("$baseUrl/api/v1/memories/proposals/$changeId/restore").ensureJsonSuccess()
        }

    private companion object {
        const val CHANGE_WITHDRAWN = "change_withdrawn"
    }
}
