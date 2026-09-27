package com.homelab.household.data.datasource.remote

import com.homelab.household.data.di.DEFAULT_BASE_URL
import com.homelab.household.data.dto.CalendarCredentialCreateDto
import com.homelab.household.domain.exception.CalendarRejectedException
import com.homelab.household.domain.exception.CalendarUnreachableException
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.exception.UpstreamGatewayException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** The wire for connecting the member's calendar, and the two ways the hub's test of it can fail. */
class KtorCalendarRemoteDataSourceTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val calendarJson = """{
        "id": "cal-1",
        "user_id": "emma",
        "provider": "apple_icloud",
        "url": "https://caldav.icloud.com",
        "username": "emma@icloud.com",
        "calendar_name": "Default",
        "is_active": true,
        "created_at": "2026-09-26T20:00:00",
        "updated_at": "2026-09-26T20:04:00"
    }"""

    private val icloud =
        CalendarCredentialCreateDto(
            provider = "apple_icloud",
            url = "https://caldav.icloud.com",
            username = "emma@icloud.com",
            password = "abcd-efgh-ijkl-mnop",
        )

    private fun MockRequestHandleScope.respondJson(
        content: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): HttpResponseData = respond(content, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun dataSource(engine: MockEngine) =
        KtorCalendarRemoteDataSource(
            client = HttpClient(engine) { install(ContentNegotiation) { json(json) } },
            baseUrl = DEFAULT_BASE_URL,
        )

    private fun assertSameJson(
        expected: String,
        actual: String?,
    ) = assertEquals(Json.parseToJsonElement(expected), Json.parseToJsonElement(actual ?: "null"))

    // ---- getMyCalendar ------------------------------------------------------

    @Test
    fun `GIVEN a connected calendar WHEN it is asked for THEN the hub's own address answers it`() =
        runTest {
            var path: String? = null
            val engine =
                MockEngine { request ->
                    path = request.url.encodedPath
                    respondJson(calendarJson)
                }

            val calendar = dataSource(engine).getMyCalendar()

            assertEquals("/api/v1/integrations/calendars/me", path)
            assertEquals("emma@icloud.com", calendar?.username)
            assertEquals("2026-09-26T20:04:00", calendar?.updatedAt)
        }

    @Test
    fun `GIVEN no calendar WHEN it is asked for THEN the hub's 404 reads as none`() =
        runTest {
            val engine = MockEngine { respondJson("""{"detail":"No calendar configured."}""", HttpStatusCode.NotFound) }

            assertNull(dataSource(engine).getMyCalendar())
        }

    @Test
    fun `GIVEN the hub cannot be reached WHEN the calendar is asked for THEN it is reported as offline`() =
        runTest {
            val engine = MockEngine { throw IOException("Connection refused") }

            assertFailsWith<ServerOfflineException> { dataSource(engine).getMyCalendar() }
        }

    // ---- configureCalendar --------------------------------------------------

    @Test
    fun `GIVEN details that work WHEN the calendar is connected THEN they are posted and the saved calendar comes back`() =
        runTest {
            var method: HttpMethod? = null
            var path: String? = null
            var body: String? = null
            val engine =
                MockEngine { request ->
                    method = request.method
                    path = request.url.encodedPath
                    body = (request.body as TextContent).text
                    respondJson(calendarJson, HttpStatusCode.Created)
                }

            val saved = dataSource(engine).configureCalendar(icloud)

            assertEquals(HttpMethod.Post, method)
            assertEquals("/api/v1/integrations/calendars", path)
            // No calendar named: the field is left out and the hub picks the account's first one.
            assertSameJson(
                """{"provider":"apple_icloud","url":"https://caldav.icloud.com","username":"emma@icloud.com",
                    "password":"abcd-efgh-ijkl-mnop"}""",
                body,
            )
            assertEquals("cal-1", saved.id)
        }

    @Test
    fun `GIVEN the calendar refuses the password WHEN it is connected THEN it says rejected`() =
        runTest {
            val engine =
                MockEngine {
                    respondJson(
                        """{"detail":"refused","code":"calendar_rejected"}""",
                        HttpStatusCode.BadRequest,
                    )
                }

            assertFailsWith<CalendarRejectedException> { dataSource(engine).configureCalendar(icloud) }
        }

    @Test
    fun `GIVEN the hub cannot reach the calendar WHEN it is connected THEN it says unreachable`() =
        runTest {
            val engine =
                MockEngine {
                    respondJson(
                        """{"detail":"could not be reached","code":"calendar_unreachable"}""",
                        HttpStatusCode.BadRequest,
                    )
                }

            assertFailsWith<CalendarUnreachableException> { dataSource(engine).configureCalendar(icloud) }
        }

    @Test
    fun `GIVEN any other refusal WHEN the calendar is connected THEN it is the hub answering badly`() =
        runTest {
            val engine = MockEngine { respondJson("""{"detail":"Invalid URL"}""", HttpStatusCode.BadRequest) }

            assertFailsWith<UpstreamGatewayException> { dataSource(engine).configureCalendar(icloud) }
        }

    // ---- deleteCalendar -----------------------------------------------------

    @Test
    fun `GIVEN a connected calendar WHEN it is removed THEN the calendars address is asked to delete it`() =
        runTest {
            var method: HttpMethod? = null
            var path: String? = null
            val engine =
                MockEngine { request ->
                    method = request.method
                    path = request.url.encodedPath
                    respond("", HttpStatusCode.NoContent)
                }

            dataSource(engine).deleteCalendar()

            assertEquals(HttpMethod.Delete, method)
            assertEquals("/api/v1/integrations/calendars", path)
        }
}
