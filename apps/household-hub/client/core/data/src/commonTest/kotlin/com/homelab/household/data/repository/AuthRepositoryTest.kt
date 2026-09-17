package com.homelab.household.data.repository

import com.homelab.household.data.di.DEFAULT_BASE_URL
import com.homelab.household.data.local.InMemoryTokenStorage
import com.homelab.household.domain.exception.CodeGuessesLockedException
import com.homelab.household.domain.exception.HubAlreadySetUpException
import com.homelab.household.domain.exception.InviteInvalidException
import com.homelab.household.domain.exception.NameTakenException
import com.homelab.household.domain.exception.NotFoundException
import com.homelab.household.domain.exception.PinLockedException
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.exception.UnauthorizedException
import com.homelab.household.domain.exception.WrongPinException
import com.homelab.household.domain.model.Member
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AuthRepositoryTest {

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

    private fun MockRequestHandleScope.respondJson(content: String, status: HttpStatusCode = HttpStatusCode.OK): HttpResponseData =
        respond(content, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun repo(engine: MockEngine, tokenStorage: InMemoryTokenStorage = InMemoryTokenStorage()) =
        AuthRepositoryImpl(
            HttpClient(engine) { install(ContentNegotiation) { json(json) } },
            tokenStorage,
            baseUrl = DEFAULT_BASE_URL
        )

    private fun assertSameJson(expected: String, actual: String?) =
        assertEquals(Json.parseToJsonElement(expected), Json.parseToJsonElement(actual ?: "null"))

    @Test
    fun signing_in_sends_the_member_and_pin_and_keeps_the_token() = runTest {
        var sent: String? = null
        val engine = MockEngine { request ->
            if (request.url.encodedPath == "/api/v1/auth/login") {
                sent = (request.body as TextContent).text
                respondJson("""{"access_token": "jwt-token-123", "token_type": "bearer", "user": $emmaJson}""")
            } else {
                respond("Not Found", HttpStatusCode.NotFound)
            }
        }
        val tokenStorage = InMemoryTokenStorage()

        val user = repo(engine, tokenStorage).login("emma", "482913")

        assertEquals("emma", user.id)
        assertEquals("Emma", user.fullName)
        assertEquals("jwt-token-123", tokenStorage.getAccessToken())
        assertSameJson("""{"user_id": "emma", "pin": "482913"}""", sent)
    }

    @Test
    fun a_wrong_pin_says_how_many_attempts_are_left_and_keeps_no_token() = runTest {
        val engine = MockEngine {
            respondJson("""{"detail": "Wrong PIN", "attempts_left": 3}""", HttpStatusCode.Unauthorized)
        }
        val tokenStorage = InMemoryTokenStorage()

        val e = assertFailsWith<WrongPinException> { repo(engine, tokenStorage).login("emma", "000000") }
        assertEquals(3, e.attemptsLeft)
        assertNull(tokenStorage.getAccessToken())
    }

    @Test
    fun a_locked_member_says_how_long_to_wait() = runTest {
        val engine = MockEngine {
            respondJson("""{"detail": "Too many wrong PINs", "retry_after_seconds": 30}""", HttpStatusCode.TooManyRequests)
        }

        val e = assertFailsWith<PinLockedException> { repo(engine).login("emma", "000000") }
        assertEquals(30, e.retryAfterSeconds)
    }

    @Test
    fun a_refusal_without_attempts_is_plain_unauthorized() = runTest {
        val engine = MockEngine { respondJson("""{"detail": "Wrong PIN"}""", HttpStatusCode.Unauthorized) }

        assertFailsWith<UnauthorizedException> { repo(engine).login("gone", "482913") }
    }

    @Test
    fun signing_in_with_the_hub_unreachable_throws_server_offline() = runTest {
        val engine = MockEngine { throw IOException("Connection refused") }

        assertFailsWith<ServerOfflineException> { repo(engine).login("emma", "482913") }
    }

    @Test
    fun first_run_posts_the_name_pin_and_colour_and_keeps_the_token() = runTest {
        var sent: String? = null
        val engine = MockEngine { request ->
            if (request.url.encodedPath == "/api/v1/auth/register-initial") {
                sent = (request.body as TextContent).text
                respondJson("""{"access_token": "first-token", "user": $emmaJson}""", HttpStatusCode.Created)
            } else {
                respond("Not Found", HttpStatusCode.NotFound)
            }
        }
        val tokenStorage = InMemoryTokenStorage()

        val user = repo(engine, tokenStorage).onboard("Emma", "482913", "#C05638")

        assertEquals("emma", user.id)
        assertEquals("first-token", tokenStorage.getAccessToken())
        assertSameJson("""{"full_name": "Emma", "pin": "482913", "avatar_color": "#C05638"}""", sent)
    }

    @Test
    fun first_run_on_a_hub_that_already_has_members_says_so() = runTest {
        val engine = MockEngine {
            respondJson("""{"detail": "System is already initialized."}""", HttpStatusCode.BadRequest)
        }

        assertFailsWith<HubAlreadySetUpException> { repo(engine).onboard("Emma", "482913", "#3C6E4E") }
    }

    @Test
    fun the_member_list_comes_back_as_members() = runTest {
        val engine = MockEngine { request ->
            if (request.url.encodedPath == "/api/v1/auth/members") {
                respondJson(
                    """[
                        {"id": "emma", "full_name": "Emma", "avatar_color": "#3C6E4E"},
                        {"id": "liam", "full_name": "Liam", "avatar_color": "#C05638"}
                    ]"""
                )
            } else {
                respond("Not Found", HttpStatusCode.NotFound)
            }
        }

        val members = repo(engine).listMembers()

        assertEquals(
            listOf(Member("emma", "Emma", "#3C6E4E"), Member("liam", "Liam", "#C05638")),
            members
        )
    }

    @Test
    fun the_member_list_behind_a_dead_proxy_throws_server_offline() = runTest {
        val engine = MockEngine { respond("Bad Gateway", HttpStatusCode.BadGateway) }

        assertFailsWith<ServerOfflineException> { repo(engine).listMembers() }
    }

    @Test
    fun check_status_when_hub_unreachable_throws_server_offline() = runTest {
        val engine = MockEngine { throw IOException("Connection refused") }

        assertFailsWith<ServerOfflineException> { repo(engine).checkStatus() }
    }

    @Test
    fun check_status_when_proxy_returns_502_bad_gateway_throws_server_offline() = runTest {
        val engine = MockEngine {
            respond(
                content = "Bad Gateway",
                status = HttpStatusCode.BadGateway,
                headers = headersOf(HttpHeaders.ContentType, "text/plain")
            )
        }

        assertFailsWith<ServerOfflineException> { repo(engine).checkStatus() }
    }

    @Test
    fun check_status_when_proxy_returns_404_html_throws_domain_exception() = runTest {
        val engine = MockEngine {
            respond(
                content = "<html><body>404 Not Found</body></html>",
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8")
            )
        }

        // 404 on the status endpoint means the address is wrong, not that the hub is down.
        assertFailsWith<NotFoundException> { repo(engine).checkStatus() }
    }

    @Test
    fun a_phone_with_a_kept_token_has_a_stored_session_without_asking_the_hub() {
        var calls = 0
        val engine = MockEngine {
            calls++
            respond("Not Found", HttpStatusCode.NotFound)
        }
        val tokenStorage = InMemoryTokenStorage()
        val repo = repo(engine, tokenStorage)

        assertEquals(false, repo.hasStoredSession())

        tokenStorage.saveTokens("token-from-last-time")

        assertEquals(true, repo.hasStoredSession())
        assertEquals(0, calls)
    }

    @Test
    fun renewing_sends_the_kept_token_and_keeps_the_fresh_one() = runTest {
        var sentWith: String? = null
        val engine = MockEngine { request ->
            if (request.url.encodedPath == "/api/v1/auth/refresh") {
                sentWith = request.headers[HttpHeaders.Authorization]
                respondJson("""{"access_token": "fresh-token", "token_type": "bearer", "user": $emmaJson}""")
            } else {
                respond("Not Found", HttpStatusCode.NotFound)
            }
        }
        val tokenStorage = InMemoryTokenStorage().apply { saveTokens("kept-token") }
        val repo = repo(engine, tokenStorage)

        assertEquals("fresh-token", repo.refreshToken())

        assertEquals("Bearer kept-token", sentWith)
        assertEquals("fresh-token", tokenStorage.getAccessToken())
        assertEquals("emma", repo.observeCurrentUser().first()?.id)
    }

    @Test
    @OptIn(ExperimentalAtomicApi::class)
    fun renewals_asked_for_together_reach_the_hub_once() = runTest {
        val refreshCount = AtomicInt(0)
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/auth/refresh" -> {
                    refreshCount.addAndFetch(1)
                    respondJson("""{"access_token": "fresh-token", "token_type": "bearer", "user": $emmaJson}""")
                }
                else -> respond("Not Found", HttpStatusCode.NotFound)
            }
        }
        val tokenStorage = InMemoryTokenStorage().apply { saveTokens("kept-token") }
        val repo = repo(engine, tokenStorage)

        val results = (1..5).map { async { repo.refreshToken() } }.awaitAll()

        assertEquals(5, results.size)
        assertEquals(1, refreshCount.load())
        assertEquals("fresh-token", tokenStorage.getAccessToken())
    }

    @Test
    fun renewing_with_the_hub_unreachable_keeps_the_token() = runTest {
        val engine = MockEngine { throw IOException("Connection refused") }
        val tokenStorage = InMemoryTokenStorage().apply { saveTokens("kept-token") }

        assertFailsWith<ServerOfflineException> { repo(engine, tokenStorage).refreshToken() }
        assertEquals("kept-token", tokenStorage.getAccessToken())
    }

    @Test
    fun a_renewal_the_hub_refuses_is_unauthorized() = runTest {
        val engine = MockEngine { respondJson("""{"detail": "Invalid or expired token."}""", HttpStatusCode.Unauthorized) }
        val tokenStorage = InMemoryTokenStorage().apply { saveTokens("revoked-token") }

        assertFailsWith<UnauthorizedException> { repo(engine, tokenStorage).refreshToken() }
    }

    @Test
    fun looking_up_an_invite_returns_who_invited_whom() = runTest {
        val engine = MockEngine { request ->
            if (request.url.encodedPath == "/api/v1/invites/482913") {
                respondJson(
                    """{"invited_name": "Liam", "inviter_name": "Emma", "inviter_avatar_color": "#3C6E4E"}"""
                )
            } else {
                respond("Not Found", HttpStatusCode.NotFound)
            }
        }

        val preview = repo(engine).lookUpInvite("482913")

        assertEquals("Liam", preview.invitedName)
        assertEquals("Emma", preview.inviterName)
        assertEquals("#3C6E4E", preview.inviterAvatarColor)
    }

    @Test
    fun looking_up_an_invalid_invite_says_so() = runTest {
        val engine = MockEngine { respondJson("""{"detail": "That code isn't valid.", "code": "invite_invalid"}""", HttpStatusCode.BadRequest) }

        assertFailsWith<InviteInvalidException> { repo(engine).lookUpInvite("bad-code") }
    }

    @Test
    fun looking_up_an_invite_when_guesses_are_locked_says_how_long_to_wait() = runTest {
        val engine = MockEngine {
            respondJson(
                """{"detail": "Too many attempts.", "code": "code_guesses_locked", "retry_after_seconds": 60}""",
                HttpStatusCode.TooManyRequests
            )
        }

        val e = assertFailsWith<CodeGuessesLockedException> { repo(engine).lookUpInvite("482913") }
        assertEquals(60, e.retryAfterSeconds)
    }

    @Test
    fun looking_up_an_invite_with_the_hub_unreachable_throws_server_offline() = runTest {
        val engine = MockEngine { throw IOException("Connection refused") }

        assertFailsWith<ServerOfflineException> { repo(engine).lookUpInvite("482913") }
    }

    @Test
    fun joining_a_household_sends_the_name_pin_and_colour_and_keeps_the_token() = runTest {
        var sent: String? = null
        val engine = MockEngine { request ->
            if (request.url.encodedPath == "/api/v1/invites/482913/redeem") {
                sent = (request.body as TextContent).text
                respondJson("""{"access_token": "joined-token", "token_type": "bearer", "user": $emmaJson}""", HttpStatusCode.Created)
            } else {
                respond("Not Found", HttpStatusCode.NotFound)
            }
        }
        val tokenStorage = InMemoryTokenStorage()

        val user = repo(engine, tokenStorage).joinHousehold("482913", "Emma", "482913", "#C05638")

        assertEquals("emma", user.id)
        assertEquals("joined-token", tokenStorage.getAccessToken())
        assertSameJson("""{"full_name": "Emma", "pin": "482913", "avatar_color": "#C05638"}""", sent)
    }

    @Test
    fun joining_with_an_invalid_code_says_so() = runTest {
        val engine = MockEngine { respondJson("""{"detail": "That code isn't valid.", "code": "invite_invalid"}""", HttpStatusCode.BadRequest) }

        assertFailsWith<InviteInvalidException> { repo(engine).joinHousehold("bad-code", "Emma", "482913", "#C05638") }
    }

    @Test
    fun joining_with_a_name_already_taken_says_so() = runTest {
        val engine = MockEngine { respondJson("""{"detail": "That name is taken.", "code": "name_taken"}""", HttpStatusCode.Conflict) }

        assertFailsWith<NameTakenException> { repo(engine).joinHousehold("482913", "Emma", "482913", "#C05638") }
    }

    @Test
    fun joining_when_guesses_are_locked_says_how_long_to_wait() = runTest {
        val engine = MockEngine {
            respondJson(
                """{"detail": "Too many attempts.", "code": "code_guesses_locked", "retry_after_seconds": 45}""",
                HttpStatusCode.TooManyRequests
            )
        }

        val e = assertFailsWith<CodeGuessesLockedException> { repo(engine).joinHousehold("482913", "Emma", "482913", "#C05638") }
        assertEquals(45, e.retryAfterSeconds)
    }

    @Test
    fun joining_with_the_hub_unreachable_throws_server_offline() = runTest {
        val engine = MockEngine { throw IOException("Connection refused") }

        assertFailsWith<ServerOfflineException> { repo(engine).joinHousehold("482913", "Emma", "482913", "#C05638") }
    }

    @Test
    fun redeeming_a_pin_reset_sends_the_pin_and_keeps_the_token() = runTest {
        var sent: String? = null
        val engine = MockEngine { request ->
            if (request.url.encodedPath == "/api/v1/auth/pin-resets/738291/redeem") {
                sent = (request.body as TextContent).text
                respondJson("""{"access_token": "reset-token", "token_type": "bearer", "user": $emmaJson}""")
            } else {
                respond("Not Found", HttpStatusCode.NotFound)
            }
        }
        val tokenStorage = InMemoryTokenStorage()

        val user = repo(engine, tokenStorage).redeemPinReset("738291", "111111")

        assertEquals("emma", user.id)
        assertEquals("reset-token", tokenStorage.getAccessToken())
        assertSameJson("""{"pin": "111111"}""", sent)
    }

    @Test
    fun redeeming_an_invalid_pin_reset_code_says_so() = runTest {
        val engine = MockEngine { respondJson("""{"detail": "That reset code isn't valid.", "code": "invite_invalid"}""", HttpStatusCode.BadRequest) }

        assertFailsWith<InviteInvalidException> { repo(engine).redeemPinReset("bad-code", "111111") }
    }

    @Test
    fun redeeming_a_pin_reset_when_guesses_are_locked_says_how_long_to_wait() = runTest {
        val engine = MockEngine {
            respondJson(
                """{"detail": "Too many attempts.", "code": "code_guesses_locked", "retry_after_seconds": 30}""",
                HttpStatusCode.TooManyRequests
            )
        }

        val e = assertFailsWith<CodeGuessesLockedException> { repo(engine).redeemPinReset("738291", "111111") }
        assertEquals(30, e.retryAfterSeconds)
    }

    @Test
    fun redeeming_a_pin_reset_with_the_hub_unreachable_throws_server_offline() = runTest {
        val engine = MockEngine { throw IOException("Connection refused") }

        assertFailsWith<ServerOfflineException> { repo(engine).redeemPinReset("738291", "111111") }
    }
}
