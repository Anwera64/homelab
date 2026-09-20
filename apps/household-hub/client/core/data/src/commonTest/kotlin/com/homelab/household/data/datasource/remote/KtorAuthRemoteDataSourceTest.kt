package com.homelab.household.data.datasource.remote

import com.homelab.household.data.di.DEFAULT_BASE_URL
import com.homelab.household.domain.exception.CodeGuessesLockedException
import com.homelab.household.domain.exception.HubAlreadySetUpException
import com.homelab.household.domain.exception.InviteInvalidException
import com.homelab.household.domain.exception.NameTakenException
import com.homelab.household.domain.exception.NotFoundException
import com.homelab.household.domain.exception.PinLockedException
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.exception.UnauthorizedException
import com.homelab.household.domain.exception.ValidationException
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
 * One call in, one DTO out. Everything this file tests is the wire: the path, the body sent, and
 * which of the hub's answers becomes which domain exception. Nothing here caches, maps to a domain
 * model or decides anything — that is [com.homelab.household.data.repository.AuthRepositoryImpl],
 * whose own tests mock this interface and never touch Ktor.
 */
class KtorAuthRemoteDataSourceTest {

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

    private fun dataSource(engine: MockEngine) = KtorAuthRemoteDataSource(
        client = HttpClient(engine) { install(ContentNegotiation) { json(json) } },
        baseUrl = DEFAULT_BASE_URL,
    )

    private fun assertSameJson(expected: String, actual: String?) =
        assertEquals(Json.parseToJsonElement(expected), Json.parseToJsonElement(actual ?: "null"))

    private fun unreachableHub() = MockEngine { throw IOException("Connection refused") }

    // ---- login -------------------------------------------------------------

    @Test
    fun `GIVEN a hub that accepts the PIN WHEN signing in THEN the member and pin are posted and the token comes back`() = runTest {
        // GIVEN
        var path: String? = null
        var sent: String? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            sent = (request.body as TextContent).text
            respondJson("""{"access_token": "jwt-token-123", "token_type": "bearer", "user": $emmaJson}""")
        }

        // WHEN
        val token = dataSource(engine).login("emma", "482913")

        // THEN
        assertEquals("/api/v1/auth/login", path)
        assertSameJson("""{"user_id": "emma", "pin": "482913"}""", sent)
        assertEquals("jwt-token-123", token.access_token)
        assertEquals("emma", token.user?.id)
    }

    @Test
    fun `GIVEN a hub that refuses the PIN with three attempts left WHEN signing in THEN it says three remain`() = runTest {
        // GIVEN
        val engine = MockEngine { respondJson("""{"detail": "Wrong PIN", "attempts_left": 3}""", HttpStatusCode.Unauthorized) }

        // WHEN
        val thrown = assertFailsWith<WrongPinException> { dataSource(engine).login("emma", "000000") }

        // THEN
        assertEquals(3, thrown.attemptsLeft)
    }

    @Test
    fun `GIVEN a hub that has locked the member out WHEN signing in THEN it says how long to wait`() = runTest {
        // GIVEN
        val engine = MockEngine {
            respondJson("""{"detail": "Too many wrong PINs", "retry_after_seconds": 30}""", HttpStatusCode.TooManyRequests)
        }

        // WHEN
        val thrown = assertFailsWith<PinLockedException> { dataSource(engine).login("emma", "000000") }

        // THEN
        assertEquals(30, thrown.retryAfterSeconds)
    }

    @Test
    fun `GIVEN a refusal that does not say how many attempts are left WHEN signing in THEN it is a plain unauthorized`() = runTest {
        // GIVEN
        val engine = MockEngine { respondJson("""{"detail": "Wrong PIN"}""", HttpStatusCode.Unauthorized) }

        // WHEN / THEN
        assertFailsWith<UnauthorizedException> { dataSource(engine).login("gone", "482913") }
    }

    @Test
    fun `GIVEN the hub cannot be reached WHEN signing in THEN it is reported as offline`() = runTest {
        // GIVEN
        val engine = unreachableHub()

        // WHEN / THEN
        assertFailsWith<ServerOfflineException> { dataSource(engine).login("emma", "482913") }
    }

    // ---- onboard -----------------------------------------------------------

    @Test
    fun `GIVEN an empty hub WHEN the first member onboards THEN the name pin and colour are posted and the token comes back`() = runTest {
        // GIVEN
        var path: String? = null
        var sent: String? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            sent = (request.body as TextContent).text
            respondJson("""{"access_token": "first-token", "user": $emmaJson}""", HttpStatusCode.Created)
        }

        // WHEN
        val token = dataSource(engine).onboard("Emma", "482913", "#C05638")

        // THEN
        assertEquals("/api/v1/auth/register-initial", path)
        assertSameJson("""{"full_name": "Emma", "pin": "482913", "avatar_color": "#C05638"}""", sent)
        assertEquals("first-token", token.access_token)
    }

    @Test
    fun `GIVEN a hub that already has members WHEN onboarding THEN it says the hub is already set up`() = runTest {
        // GIVEN
        val engine = MockEngine { respondJson("""{"detail": "System is already initialized."}""", HttpStatusCode.BadRequest) }

        // WHEN / THEN
        assertFailsWith<HubAlreadySetUpException> { dataSource(engine).onboard("Emma", "482913", "#3C6E4E") }
    }

    @Test
    fun `GIVEN a hub that refuses the name or PIN WHEN onboarding THEN it is a validation failure`() = runTest {
        // GIVEN
        val engine = MockEngine { respondJson("""{"detail": "too short"}""", HttpStatusCode.UnprocessableEntity) }

        // WHEN / THEN
        assertFailsWith<ValidationException> { dataSource(engine).onboard("Emma", "1", "#3C6E4E") }
    }

    // ---- listMembers -------------------------------------------------------

    @Test
    fun `GIVEN a hub with two members WHEN the member list is asked for THEN both profiles come back`() = runTest {
        // GIVEN
        var path: String? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            respondJson(
                """[
                    {"id": "emma", "full_name": "Emma", "avatar_color": "#3C6E4E"},
                    {"id": "liam", "full_name": "Liam", "avatar_color": "#C05638"}
                ]"""
            )
        }

        // WHEN
        val members = dataSource(engine).listMembers()

        // THEN
        assertEquals("/api/v1/auth/members", path)
        assertEquals(listOf("emma", "liam"), members.map { it.id })
        assertEquals(listOf("Emma", "Liam"), members.map { it.full_name })
    }

    @Test
    fun `GIVEN a dead proxy in front of the hub WHEN the member list is asked for THEN the hub is reported offline`() = runTest {
        // GIVEN
        val engine = MockEngine { respond("Bad Gateway", HttpStatusCode.BadGateway) }

        // WHEN / THEN
        assertFailsWith<ServerOfflineException> { dataSource(engine).listMembers() }
    }

    // ---- checkStatus -------------------------------------------------------

    @Test
    fun `GIVEN a hub that has been set up WHEN its status is asked for THEN it says so and how many members it has`() = runTest {
        // GIVEN
        var path: String? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            respondJson("""{"is_initialized": true, "member_count": 4}""")
        }

        // WHEN
        val status = dataSource(engine).checkStatus()

        // THEN
        assertEquals("/api/v1/auth/status", path)
        assertEquals(true, status.is_initialized)
        assertEquals(4, status.member_count)
    }

    @Test
    fun `GIVEN the hub cannot be reached WHEN its status is asked for THEN it is reported as offline`() = runTest {
        // GIVEN
        val engine = unreachableHub()

        // WHEN / THEN
        assertFailsWith<ServerOfflineException> { dataSource(engine).checkStatus() }
    }

    @Test
    fun `GIVEN a proxy answering 502 WHEN the hub status is asked for THEN the hub is reported offline rather than broken`() = runTest {
        // GIVEN
        val engine = MockEngine {
            respond("Bad Gateway", HttpStatusCode.BadGateway, headersOf(HttpHeaders.ContentType, "text/plain"))
        }

        // WHEN / THEN
        assertFailsWith<ServerOfflineException> { dataSource(engine).checkStatus() }
    }

    @Test
    fun `GIVEN a 404 HTML page where the hub should be WHEN its status is asked for THEN the address is reported as wrong`() = runTest {
        // GIVEN — 404 means the address is wrong, not that the hub is down.
        val engine = MockEngine {
            respond(
                "<html><body>404 Not Found</body></html>",
                HttpStatusCode.NotFound,
                headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"),
            )
        }

        // WHEN / THEN
        assertFailsWith<NotFoundException> { dataSource(engine).checkStatus() }
    }

    // ---- lookUpInvite ------------------------------------------------------

    @Test
    fun `GIVEN a live invite code WHEN it is looked up THEN it says who invited whom`() = runTest {
        // GIVEN
        var path: String? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            respondJson("""{"invited_name": "Liam", "inviter_name": "Emma", "inviter_avatar_color": "#3C6E4E"}""")
        }

        // WHEN
        val preview = dataSource(engine).lookUpInvite("482913")

        // THEN
        assertEquals("/api/v1/invites/482913", path)
        assertEquals("Liam", preview.invited_name)
        assertEquals("Emma", preview.inviter_name)
    }

    @Test
    fun `GIVEN an invite code the hub does not know WHEN it is looked up THEN it says the code is invalid`() = runTest {
        // GIVEN
        val engine = MockEngine { respondJson("""{"detail": "Invalid invite"}""", HttpStatusCode.BadRequest) }

        // WHEN / THEN
        assertFailsWith<InviteInvalidException> { dataSource(engine).lookUpInvite("000000") }
    }

    @Test
    fun `GIVEN too many wrong invite codes WHEN another is looked up THEN it says how long to wait`() = runTest {
        // GIVEN
        val engine = MockEngine {
            respondJson("""{"detail": "Too many attempts", "retry_after_seconds": 60}""", HttpStatusCode.TooManyRequests)
        }

        // WHEN
        val thrown = assertFailsWith<CodeGuessesLockedException> { dataSource(engine).lookUpInvite("000000") }

        // THEN
        assertEquals(60, thrown.retryAfterSeconds)
    }

    @Test
    fun `GIVEN the hub cannot be reached WHEN an invite is looked up THEN it is reported as offline`() = runTest {
        // GIVEN
        val engine = unreachableHub()

        // WHEN / THEN
        assertFailsWith<ServerOfflineException> { dataSource(engine).lookUpInvite("482913") }
    }

    // ---- joinHousehold -----------------------------------------------------

    @Test
    fun `GIVEN a live invite code WHEN it is redeemed THEN the name pin and colour are posted and the token comes back`() = runTest {
        // GIVEN
        var path: String? = null
        var sent: String? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            sent = (request.body as TextContent).text
            respondJson("""{"access_token": "joined-token", "user": $emmaJson}""")
        }

        // WHEN
        val token = dataSource(engine).joinHousehold("482913", "Liam", "112233", "#C05638")

        // THEN
        assertEquals("/api/v1/invites/482913/redeem", path)
        assertSameJson("""{"full_name": "Liam", "pin": "112233", "avatar_color": "#C05638"}""", sent)
        assertEquals("joined-token", token.access_token)
    }

    @Test
    fun `GIVEN an invite code the hub does not know WHEN it is redeemed THEN it says the code is invalid`() = runTest {
        // GIVEN
        val engine = MockEngine { respondJson("""{"detail": "Invalid invite"}""", HttpStatusCode.BadRequest) }

        // WHEN / THEN
        assertFailsWith<InviteInvalidException> { dataSource(engine).joinHousehold("000000", "Liam", "112233", "#C05638") }
    }

    @Test
    fun `GIVEN a name another member already uses WHEN an invite is redeemed THEN it says the name is taken`() = runTest {
        // GIVEN
        val engine = MockEngine { respondJson("""{"detail": "Name taken"}""", HttpStatusCode.Conflict) }

        // WHEN / THEN
        assertFailsWith<NameTakenException> { dataSource(engine).joinHousehold("482913", "Emma", "112233", "#C05638") }
    }

    @Test
    fun `GIVEN too many wrong invite codes WHEN another is redeemed THEN it says how long to wait`() = runTest {
        // GIVEN
        val engine = MockEngine {
            respondJson("""{"detail": "Too many attempts", "retry_after_seconds": 45}""", HttpStatusCode.TooManyRequests)
        }

        // WHEN
        val thrown = assertFailsWith<CodeGuessesLockedException> {
            dataSource(engine).joinHousehold("000000", "Liam", "112233", "#C05638")
        }

        // THEN
        assertEquals(45, thrown.retryAfterSeconds)
    }

    @Test
    fun `GIVEN the hub cannot be reached WHEN an invite is redeemed THEN it is reported as offline`() = runTest {
        // GIVEN
        val engine = unreachableHub()

        // WHEN / THEN
        assertFailsWith<ServerOfflineException> { dataSource(engine).joinHousehold("482913", "Liam", "112233", "#C05638") }
    }

    // ---- redeemPinReset ----------------------------------------------------

    @Test
    fun `GIVEN a live reset code WHEN it is redeemed THEN the new pin is posted and the token comes back`() = runTest {
        // GIVEN
        var path: String? = null
        var sent: String? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            sent = (request.body as TextContent).text
            respondJson("""{"access_token": "reset-token", "user": $emmaJson}""")
        }

        // WHEN
        val token = dataSource(engine).redeemPinReset("K7M2QP", "998877")

        // THEN
        assertEquals("/api/v1/auth/pin-resets/K7M2QP/redeem", path)
        assertSameJson("""{"pin": "998877"}""", sent)
        assertEquals("reset-token", token.access_token)
    }

    @Test
    fun `GIVEN a reset code the hub does not know WHEN it is redeemed THEN it says the code is invalid`() = runTest {
        // GIVEN
        val engine = MockEngine { respondJson("""{"detail": "Invalid code"}""", HttpStatusCode.BadRequest) }

        // WHEN / THEN
        assertFailsWith<InviteInvalidException> { dataSource(engine).redeemPinReset("000000", "998877") }
    }

    @Test
    fun `GIVEN too many wrong reset codes WHEN another is redeemed THEN it says how long to wait`() = runTest {
        // GIVEN
        val engine = MockEngine {
            respondJson("""{"detail": "Too many attempts", "retry_after_seconds": 20}""", HttpStatusCode.TooManyRequests)
        }

        // WHEN
        val thrown = assertFailsWith<CodeGuessesLockedException> { dataSource(engine).redeemPinReset("000000", "998877") }

        // THEN
        assertEquals(20, thrown.retryAfterSeconds)
    }

    @Test
    fun `GIVEN the hub cannot be reached WHEN a reset code is redeemed THEN it is reported as offline`() = runTest {
        // GIVEN
        val engine = unreachableHub()

        // WHEN / THEN
        assertFailsWith<ServerOfflineException> { dataSource(engine).redeemPinReset("K7M2QP", "998877") }
    }

    // ---- renew -------------------------------------------------------------

    @Test
    fun `GIVEN a token the hub still accepts WHEN it is renewed THEN the kept one is sent and a fresh one comes back`() = runTest {
        // GIVEN
        var path: String? = null
        var sentWith: String? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            sentWith = request.headers[HttpHeaders.Authorization]
            respondJson("""{"access_token": "fresh-token", "token_type": "bearer", "user": $emmaJson}""")
        }

        // WHEN
        val token = dataSource(engine).renew("kept-token")

        // THEN
        assertEquals("/api/v1/auth/refresh", path)
        assertEquals("Bearer kept-token", sentWith)
        assertEquals("fresh-token", token.access_token)
    }

    @Test
    fun `GIVEN a token the hub has stopped accepting WHEN it is renewed THEN it is unauthorized`() = runTest {
        // GIVEN
        val engine = MockEngine { respondJson("""{"detail": "Invalid or expired token."}""", HttpStatusCode.Unauthorized) }

        // WHEN / THEN
        assertFailsWith<UnauthorizedException> { dataSource(engine).renew("revoked-token") }
    }

    @Test
    fun `GIVEN the hub cannot be reached WHEN a token is renewed THEN it is reported as offline`() = runTest {
        // GIVEN
        val engine = unreachableHub()

        // WHEN / THEN
        assertFailsWith<ServerOfflineException> { dataSource(engine).renew("kept-token") }
    }

    // ---- fetchCurrentUser --------------------------------------------------

    @Test
    fun `GIVEN a signed-in member WHEN the hub is asked who they are THEN their profile comes back`() = runTest {
        // GIVEN
        var path: String? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            respondJson(emmaJson)
        }

        // WHEN
        val user = dataSource(engine).fetchCurrentUser()

        // THEN
        assertEquals("/api/v1/auth/me", path)
        assertEquals("emma", user.id)
        assertEquals("Emma", user.full_name)
    }

    @Test
    fun `GIVEN the hub cannot be reached WHEN it is asked who is signed in THEN it is reported as offline`() = runTest {
        // GIVEN
        val engine = unreachableHub()

        // WHEN / THEN
        assertFailsWith<ServerOfflineException> { dataSource(engine).fetchCurrentUser() }
    }
}
