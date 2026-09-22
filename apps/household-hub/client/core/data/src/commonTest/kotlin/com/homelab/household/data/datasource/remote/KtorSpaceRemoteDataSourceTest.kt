package com.homelab.household.data.datasource.remote

import com.homelab.household.data.di.DEFAULT_BASE_URL
import com.homelab.household.domain.exception.ServerOfflineException
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

/** The wire for the household's two spaces — the personal one and the shared one. */
class KtorSpaceRemoteDataSourceTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val personalSpaceJson = """{
        "id": "sp-personal-emma",
        "name": "Emma's space",
        "type": "personal",
        "owner_id": "emma",
        "settings": {},
        "created_at": "2026-09-13T00:00:00Z"
    }"""

    private val householdSpaceJson = """{
        "id": "sp-household",
        "name": "The Household",
        "type": "household",
        "settings": {"theme": "dark"},
        "created_at": "2026-09-13T00:00:00Z"
    }"""

    private fun MockRequestHandleScope.respondJson(
        content: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): HttpResponseData = respond(content, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun dataSource(engine: MockEngine) =
        KtorSpaceRemoteDataSource(
            client = HttpClient(engine) { install(ContentNegotiation) { json(json) } },
            baseUrl = DEFAULT_BASE_URL,
        )

    private fun assertSameJson(
        expected: String,
        actual: String?,
    ) = assertEquals(Json.parseToJsonElement(expected), Json.parseToJsonElement(actual ?: "null"))

    private fun unreachableHub() = MockEngine { throw IOException("Connection refused") }

    // ---- getPersonalSpace -----------------------------------------------------

    @Test
    fun `GIVEN a member's own space WHEN it is asked for THEN it comes back from the personal address`() =
        runTest {
            // GIVEN
            var path: String? = null
            val engine =
                MockEngine { request ->
                    path = request.url.encodedPath
                    respondJson(personalSpaceJson)
                }

            // WHEN
            val space = dataSource(engine).getPersonalSpace()

            // THEN
            assertEquals("/api/v1/spaces/personal", path)
            assertEquals("sp-personal-emma", space.id)
        }

    @Test
    fun `GIVEN the hub cannot be reached WHEN a member's own space is asked for THEN it is reported as offline`() =
        runTest {
            // GIVEN
            val engine = unreachableHub()

            // WHEN / THEN
            assertFailsWith<ServerOfflineException> { dataSource(engine).getPersonalSpace() }
        }

    // ---- getHouseholdSpace ------------------------------------------------

    @Test
    fun `GIVEN the space everybody shares WHEN it is asked for THEN it comes back from the shared address`() =
        runTest {
            // GIVEN
            var path: String? = null
            val engine =
                MockEngine { request ->
                    path = request.url.encodedPath
                    respondJson(householdSpaceJson)
                }

            // WHEN
            val space = dataSource(engine).getHouseholdSpace()

            // THEN
            assertEquals("/api/v1/spaces/shared", path)
            assertEquals("sp-household", space.id)
        }

    // ---- updateSpaceSettings -----------------------------------------------

    @Test
    fun `GIVEN new settings for a space WHEN they are saved THEN they are put to that space's own address`() =
        runTest {
            // GIVEN
            var path: String? = null
            var method: HttpMethod? = null
            var sent: String? = null
            val engine =
                MockEngine { request ->
                    path = request.url.encodedPath
                    method = request.method
                    sent = (request.body as TextContent).text
                    respondJson(householdSpaceJson)
                }

            // WHEN
            val space = dataSource(engine).updateSpaceSettings("sp-household", mapOf("theme" to "dark"))

            // THEN
            assertEquals("/api/v1/spaces/sp-household/settings", path)
            assertEquals(HttpMethod.Put, method)
            assertSameJson("""{"settings": {"theme": "dark"}}""", sent)
            assertEquals("sp-household", space.id)
        }

    @Test
    fun `GIVEN the hub cannot be reached WHEN a space's settings are saved THEN it is reported as offline`() =
        runTest {
            // GIVEN
            val engine = unreachableHub()

            // WHEN / THEN
            assertFailsWith<ServerOfflineException> {
                dataSource(engine).updateSpaceSettings("sp-household", mapOf("theme" to "dark"))
            }
        }
}
