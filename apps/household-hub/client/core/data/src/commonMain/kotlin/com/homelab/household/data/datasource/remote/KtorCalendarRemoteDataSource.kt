package com.homelab.household.data.datasource.remote

import com.homelab.household.data.datasource.remote.`interface`.CalendarRemoteDataSource
import com.homelab.household.data.dto.CalendarCredentialCreateDto
import com.homelab.household.data.dto.CalendarCredentialReadDto
import com.homelab.household.data.dto.GoogleSignInStartDto
import com.homelab.household.data.network.ensureJsonSuccess
import com.homelab.household.data.network.pinRefusal
import com.homelab.household.data.network.reachingHub
import com.homelab.household.domain.exception.CalendarRejectedException
import com.homelab.household.domain.exception.CalendarUnreachableException
import com.homelab.household.domain.exception.GoogleSignInUnavailableException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

class KtorCalendarRemoteDataSource(
    private val client: HttpClient,
    private val baseUrl: String,
) : CalendarRemoteDataSource {
    override suspend fun getMyCalendar(): CalendarCredentialReadDto? =
        reachingHub {
            val response = client.get("$baseUrl/api/v1/integrations/calendars/me")
            if (response.status == HttpStatusCode.NotFound) {
                null
            } else {
                response.ensureJsonSuccess().body<CalendarCredentialReadDto>()
            }
        }

    override suspend fun configureCalendar(credential: CalendarCredentialCreateDto): CalendarCredentialReadDto =
        reachingHub {
            val response =
                client.post("$baseUrl/api/v1/integrations/calendars") {
                    contentType(ContentType.Application.Json)
                    setBody(credential)
                }
            // The hub tests the calendar before it keeps anything, and says which way the test failed.
            if (response.status == HttpStatusCode.BadRequest) {
                when (response.pinRefusal()?.code) {
                    REJECTED -> throw CalendarRejectedException()
                    UNREACHABLE -> throw CalendarUnreachableException()
                }
            }
            response.ensureJsonSuccess().body()
        }

    override suspend fun deleteCalendar(): Unit =
        reachingHub {
            client.delete("$baseUrl/api/v1/integrations/calendars").ensureJsonSuccess()
        }

    override suspend fun startGoogleSignIn(): String =
        reachingHub {
            val response = client.post("$baseUrl/api/v1/integrations/calendars/google/start")
            // Before ensureJsonSuccess, which reads every 503 as the hub being down.
            if (response.status == HttpStatusCode.ServiceUnavailable &&
                response.pinRefusal()?.code == GOOGLE_NOT_CONFIGURED
            ) {
                throw GoogleSignInUnavailableException()
            }
            response.ensureJsonSuccess().body<GoogleSignInStartDto>().authorizationUrl
        }

    private companion object {
        const val REJECTED = "calendar_rejected"
        const val UNREACHABLE = "calendar_unreachable"
        const val GOOGLE_NOT_CONFIGURED = "google_not_configured"
    }
}
