package com.homelab.household.data.datasource.remote

import com.homelab.household.data.di.DEFAULT_BASE_URL
import com.homelab.household.domain.exception.ForbiddenException
import com.homelab.household.domain.exception.NameTakenException
import com.homelab.household.domain.exception.PinLockedException
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.exception.SoleAdminException
import com.homelab.household.domain.exception.WrongPinException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
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

/**
 * The wire for everything an admin does to the household's members. A signed-in call refuses a PIN
 * with 403 rather than 401 — see `throwIfPinRefused` — so nothing here can be mistaken for the hub
 * dropping this phone's token.
 */
class KtorMembersRemoteDataSourceTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val emmaJson = """{
        "id": "emma",
        "full_name": "Emma",
        "avatar_color": "#3C6E4E",
        "is_admin": true,
        "is_active": true,
        "personal_space_id": "sp-1",
        "created_at": "2026-09-13T00:00:00Z"
    }"""

    private fun MockRequestHandleScope.respondJson(
        content: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): HttpResponseData = respond(content, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun dataSource(engine: MockEngine) =
        KtorMembersRemoteDataSource(
            client = HttpClient(engine) { install(ContentNegotiation) { json(json) } },
            baseUrl = DEFAULT_BASE_URL,
        )

    private fun assertSameJson(
        expected: String,
        actual: String?,
    ) = assertEquals(Json.parseToJsonElement(expected), Json.parseToJsonElement(actual ?: "null"))

    private fun unreachableHub() = MockEngine { throw IOException("Connection refused") }

    // ---- listHouseholdMembers ---------------------------------------------

    @Test
    fun `GIVEN a household with one member WHEN its members are asked for THEN their full profile comes back`() =
        runTest {
            // GIVEN
            var path: String? = null
            val engine =
                MockEngine { request ->
                    path = request.url.encodedPath
                    respondJson("[$emmaJson]")
                }

            // WHEN
            val members = dataSource(engine).listHouseholdMembers()

            // THEN
            assertEquals("/api/v1/users", path)
            assertEquals(1, members.size)
            assertEquals("emma", members.first().id)
            assertEquals("sp-1", members.first().personal_space_id)
        }

    @Test
    fun `GIVEN the hub cannot be reached WHEN household members are asked for THEN it is reported as offline`() =
        runTest {
            // GIVEN
            val engine = unreachableHub()

            // WHEN / THEN
            assertFailsWith<ServerOfflineException> { dataSource(engine).listHouseholdMembers() }
        }

    // ---- createInvite ------------------------------------------------------

    @Test
    fun `GIVEN an admin inviting someone WHEN the invite is created THEN the name and admin flag are posted and a code comes back`() =
        runTest {
            // GIVEN
            var path: String? = null
            var sent: String? = null
            val engine =
                MockEngine { request ->
                    path = request.url.encodedPath
                    sent = (request.body as TextContent).text
                    respondJson(
                        """{"code": "482913", "invited_name": "Liam", "is_admin": false, "expires_in_seconds": 900}""",
                        HttpStatusCode.Created,
                    )
                }

            // WHEN
            val invite = dataSource(engine).createInvite("Liam", isAdmin = false)

            // THEN
            assertEquals("/api/v1/invites", path)
            assertSameJson("""{"invited_name": "Liam", "is_admin": false}""", sent)
            assertEquals("482913", invite.code)
            assertEquals(900, invite.expires_in_seconds)
        }

    @Test
    fun `GIVEN a name someone in the household already has WHEN an invite is created THEN it says the name is taken`() =
        runTest {
            // GIVEN — the picker tells members apart by name alone, so the hub refuses a repeat.
            val engine =
                MockEngine {
                    respondJson(
                        """{"detail": "Already has that name.", "code": "name_taken"}""",
                        HttpStatusCode.Conflict,
                    )
                }

            // WHEN / THEN
            assertFailsWith<NameTakenException> { dataSource(engine).createInvite("Emma", isAdmin = false) }
        }

    @Test
    fun `GIVEN the hub cannot be reached WHEN an invite is created THEN it is reported as offline`() =
        runTest {
            // GIVEN
            val engine = unreachableHub()

            // WHEN / THEN
            assertFailsWith<ServerOfflineException> { dataSource(engine).createInvite("Liam", isAdmin = false) }
        }

    // ---- approvePinReset ---------------------------------------------------

    @Test
    fun `GIVEN an admin approving a forgotten PIN WHEN they confirm with their own THEN their pin is posted and a reset code comes back`() =
        runTest {
            // GIVEN
            var path: String? = null
            var sent: String? = null
            val engine =
                MockEngine { request ->
                    path = request.url.encodedPath
                    sent = (request.body as TextContent).text
                    respondJson("""{"code": "738291", "expires_in_seconds": 900}""", HttpStatusCode.Created)
                }

            // WHEN
            val reset = dataSource(engine).approvePinReset("liam", "111111")

            // THEN
            assertEquals("/api/v1/users/liam/pin-resets", path)
            assertSameJson("""{"pin": "111111"}""", sent)
            assertEquals("738291", reset.code)
            assertEquals(900, reset.expires_in_seconds)
        }

    @Test
    fun `GIVEN an admin whose own PIN is wrong WHEN they approve a reset THEN it says how many attempts are left`() =
        runTest {
            // GIVEN
            val engine =
                MockEngine {
                    respondJson(
                        """{"detail": "Wrong PIN", "code": "wrong_pin", "attempts_left": 2}""",
                        HttpStatusCode.Forbidden,
                    )
                }

            // WHEN
            val thrown = assertFailsWith<WrongPinException> { dataSource(engine).approvePinReset("liam", "000000") }

            // THEN
            assertEquals(2, thrown.attemptsLeft)
        }

    @Test
    fun `GIVEN an admin locked out of PIN checks WHEN they approve a reset THEN it says how long to wait`() =
        runTest {
            // GIVEN
            val engine =
                MockEngine {
                    respondJson(
                        """{"detail": "Too many wrong PINs", "code": "pin_locked", "retry_after_seconds": 30}""",
                        HttpStatusCode.TooManyRequests,
                    )
                }

            // WHEN
            val thrown = assertFailsWith<PinLockedException> { dataSource(engine).approvePinReset("liam", "000000") }

            // THEN
            assertEquals(30, thrown.retryAfterSeconds)
        }

    @Test
    fun `GIVEN a refusal that does not say how many attempts are left WHEN a reset is approved THEN it is a plain forbidden`() =
        runTest {
            // GIVEN
            val engine = MockEngine { respondJson("""{"detail": "Not allowed"}""", HttpStatusCode.Forbidden) }

            // WHEN / THEN
            assertFailsWith<ForbiddenException> { dataSource(engine).approvePinReset("liam", "111111") }
        }

    // ---- changePin ---------------------------------------------------------

    @Test
    fun `GIVEN a member changing their PIN WHEN both pins are sent THEN a fresh token comes back`() =
        runTest {
            // GIVEN
            var path: String? = null
            var sent: String? = null
            val engine =
                MockEngine { request ->
                    path = request.url.encodedPath
                    sent = (request.body as TextContent).text
                    respondJson("""{"access_token": "new-token", "token_type": "bearer", "user": $emmaJson}""")
                }

            // WHEN
            val token = dataSource(engine).changePin("111111", "222222")

            // THEN
            assertEquals("/api/v1/users/me/pin", path)
            assertSameJson("""{"current_pin": "111111", "new_pin": "222222"}""", sent)
            assertEquals("new-token", token.access_token)
        }

    @Test
    fun `GIVEN the wrong current PIN WHEN a member changes it THEN it says how many attempts are left`() =
        runTest {
            // GIVEN
            val engine =
                MockEngine {
                    respondJson(
                        """{"detail": "Wrong PIN", "code": "wrong_pin", "attempts_left": 1}""",
                        HttpStatusCode.Forbidden,
                    )
                }

            // WHEN
            val thrown = assertFailsWith<WrongPinException> { dataSource(engine).changePin("000000", "222222") }

            // THEN
            assertEquals(1, thrown.attemptsLeft)
        }

    // ---- removeMember ------------------------------------------------------

    @Test
    fun `GIVEN an admin removing someone WHEN the hub agrees THEN the member is asked to be deleted`() =
        runTest {
            // GIVEN
            var path: String? = null
            val engine =
                MockEngine { request ->
                    path = request.url.encodedPath
                    respondJson("""{"message": "Member removed from the household"}""")
                }

            // WHEN
            dataSource(engine).removeMember("liam")

            // THEN
            assertEquals("/api/v1/users/liam", path)
        }

    @Test
    fun `GIVEN the only admin left WHEN someone tries to remove them THEN it says they are the sole admin`() =
        runTest {
            // GIVEN
            val engine =
                MockEngine {
                    respondJson(
                        """{"detail": "The only admin can't be removed.", "code": "sole_admin"}""",
                        HttpStatusCode.Conflict,
                    )
                }

            // WHEN / THEN
            assertFailsWith<SoleAdminException> { dataSource(engine).removeMember("emma") }
        }

    // ---- leaveHousehold ----------------------------------------------------

    @Test
    fun `GIVEN a member leaving WHEN they confirm with their PIN THEN it is posted with the deletion`() =
        runTest {
            // GIVEN
            var path: String? = null
            var sent: String? = null
            val engine =
                MockEngine { request ->
                    path = request.url.encodedPath
                    sent = (request.body as TextContent).text
                    respondJson("""{"message": "You have left the household"}""")
                }

            // WHEN
            dataSource(engine).leaveHousehold("111111")

            // THEN
            assertEquals("/api/v1/users/me", path)
            assertSameJson("""{"pin": "111111"}""", sent)
        }

    @Test
    fun `GIVEN the wrong PIN WHEN a member tries to leave THEN it says how many attempts are left`() =
        runTest {
            // GIVEN
            val engine =
                MockEngine {
                    respondJson(
                        """{"detail": "Wrong PIN", "code": "wrong_pin", "attempts_left": 2}""",
                        HttpStatusCode.Forbidden,
                    )
                }

            // WHEN
            val thrown = assertFailsWith<WrongPinException> { dataSource(engine).leaveHousehold("000000") }

            // THEN
            assertEquals(2, thrown.attemptsLeft)
        }

    @Test
    fun `GIVEN the only admin WHEN they try to leave THEN it says they are the sole admin`() =
        runTest {
            // GIVEN
            val engine =
                MockEngine {
                    respondJson(
                        """{"detail": "The only admin can't leave.", "code": "sole_admin"}""",
                        HttpStatusCode.Conflict,
                    )
                }

            // WHEN / THEN
            assertFailsWith<SoleAdminException> { dataSource(engine).leaveHousehold("111111") }
        }

    @Test
    fun `GIVEN the hub cannot be reached WHEN a member tries to leave THEN it is reported as offline`() =
        runTest {
            // GIVEN
            val engine = unreachableHub()

            // WHEN / THEN
            assertFailsWith<ServerOfflineException> { dataSource(engine).leaveHousehold("111111") }
        }
}
