package com.homelab.household.data.datasource.remote

import com.homelab.household.data.datasource.remote.`interface`.SessionRemoteDataSource
import com.homelab.household.data.datasource.remote.sse.DefensiveSseStreamReader
import com.homelab.household.data.dto.ChatTurnRequestDto
import com.homelab.household.data.dto.SessionCreateDto
import com.homelab.household.data.dto.SessionDetailReadDto
import com.homelab.household.data.dto.SessionReadDto
import com.homelab.household.data.dto.SessionSecretToggleDto
import com.homelab.household.data.dto.ToolApprovalRequestDto
import com.homelab.household.data.network.NetworkExceptionHelper
import com.homelab.household.data.network.ensureJsonSuccess
import com.homelab.household.data.network.reachingHub
import com.homelab.household.domain.exception.SessionConflictException
import com.homelab.household.domain.exception.UpstreamGatewayException
import com.homelab.household.domain.model.ChatStreamEvent
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow

class KtorSessionRemoteDataSource(
    private val client: HttpClient,
    private val baseUrl: String,
    private val sseStreamReader: DefensiveSseStreamReader = DefensiveSseStreamReader(),
) : SessionRemoteDataSource {

    override suspend fun listSessions(): List<SessionReadDto> = reachingHub {
        client.get("$baseUrl/api/v1/sessions").ensureJsonSuccess().body()
    }

    override suspend fun fetchSession(sessionId: String): SessionDetailReadDto = reachingHub {
        client.get("$baseUrl/api/v1/sessions/$sessionId").ensureJsonSuccess().body()
    }

    override suspend fun createSession(agentId: String, title: String, isSecret: Boolean): SessionReadDto = reachingHub {
        client.post("$baseUrl/api/v1/sessions") {
            contentType(ContentType.Application.Json)
            setBody(SessionCreateDto(agent_id = agentId, title = title, is_secret = isSecret))
        }.ensureJsonSuccess().body()
    }

    override suspend fun archiveSession(sessionId: String): Unit = reachingHub {
        client.post("$baseUrl/api/v1/sessions/$sessionId/archive").ensureJsonSuccess()
    }

    override suspend fun toggleSecretMode(sessionId: String, isSecret: Boolean): SessionReadDto = reachingHub {
        client.patch("$baseUrl/api/v1/sessions/$sessionId/secret") {
            contentType(ContentType.Application.Json)
            setBody(SessionSecretToggleDto(is_secret = isSecret))
        }.ensureJsonSuccess().body()
    }

    override suspend fun deleteSession(sessionId: String): Unit = reachingHub {
        client.delete("$baseUrl/api/v1/sessions/$sessionId").ensureJsonSuccess()
    }

    override suspend fun approveToolProposal(
        sessionId: String,
        toolCallId: String,
        approved: Boolean,
    ): Boolean = reachingHub {
        client.post("$baseUrl/api/v1/sessions/$sessionId/tools/approve") {
            contentType(ContentType.Application.Json)
            setBody(ToolApprovalRequestDto(tool_call_id = toolCallId, approved = approved))
        }.status.isSuccess()
    }

    /**
     * The only streamed call in the client, and the only place the dispatcher matters.
     *
     * `statement.execute` runs its block on the engine's dispatcher on every non-JVM target (Ktor's
     * `useEngineDispatcher` is unconditionally true there, and becomes so everywhere in Ktor 4).
     * `Flow.emit` may not cross a dispatcher boundary, so events leave the block through
     * `channelFlow`'s `send`, which is context-agnostic. RENDEZVOUS keeps the lock-step back
     * pressure a plain `flow { emit(...) }` would have — the iOS proofs in `shared/src/iosTest`
     * depend on an event reaching the collector before the next one is read off the socket.
     *
     * This is the whole reason the awkwardness is confined to one function: above here, the
     * repository composes ordinary flows and nothing crosses a dispatcher at all.
     */
    override fun openChatStream(
        sessionId: String,
        content: String,
        autoApproveWrites: Boolean,
    ): Flow<ChatStreamEvent> = channelFlow {
        try {
            val statement = client.preparePost("$baseUrl/api/v1/sessions/$sessionId/chat/stream") {
                contentType(ContentType.Application.Json)
                setBody(ChatTurnRequestDto(content = content, auto_approve_writes = autoApproveWrites))
            }
            statement.execute { response ->
                when (response.status) {
                    HttpStatusCode.OK ->
                        sseStreamReader.readEvents(response.bodyAsChannel()).collect { send(it) }
                    HttpStatusCode.Conflict ->
                        throw SessionConflictException()
                    else ->
                        throw UpstreamGatewayException(statusCode = response.status.value)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Network failures become ServerOfflineException; the refusals above pass through.
            NetworkExceptionHelper.rethrowAsDomain(e)
        }
    }.buffer(Channel.RENDEZVOUS)
}
