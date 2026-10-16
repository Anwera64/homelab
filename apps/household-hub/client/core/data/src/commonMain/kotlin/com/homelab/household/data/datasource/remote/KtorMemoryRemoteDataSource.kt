package com.homelab.household.data.datasource.remote

import com.homelab.household.data.datasource.remote.`interface`.MemoryRemoteDataSource
import com.homelab.household.data.dto.ChangeAnswerDto
import com.homelab.household.data.dto.ChangesToCheckDto
import com.homelab.household.data.dto.LookedOverDto
import com.homelab.household.data.dto.MemoryPageDto
import com.homelab.household.data.network.ensureJsonSuccess
import com.homelab.household.data.network.reachingHub
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import com.homelab.household.domain.model.ProposalAnswer

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
        answer: ProposalAnswer,
    ): Unit =
        reachingHub {
            client
                .post("$baseUrl/api/v1/memories/proposals/$changeId/answer") {
                    contentType(ContentType.Application.Json)
                    setBody(ChangeAnswerDto(answer = WIRE_ANSWERS.getValue(answer)))
                }.ensureJsonSuccess()
        }

    override suspend fun undoChangeAnswer(changeId: String): Unit =
        reachingHub {
            client.post("$baseUrl/api/v1/memories/proposals/$changeId/undo").ensureJsonSuccess()
        }

    override suspend fun getLookedOver(sessionId: String): LookedOverDto =
        reachingHub {
            client.get("$baseUrl/api/v1/sessions/$sessionId/looked-over").ensureJsonSuccess().body()
        }

    private companion object {
        val WIRE_ANSWERS =
            mapOf(
                ProposalAnswer.KeepStored to "keep_stored",
                ProposalAnswer.KeepBoth to "keep_both",
                ProposalAnswer.UseNew to "use_new",
            )
    }
}
