package com.homelab.household.data.repository

import com.homelab.household.data.di.DEFAULT_BASE_URL
import com.homelab.household.data.local.InMemoryTokenStorage
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.exception.SoleAdminException
import com.homelab.household.domain.exception.WrongPinException
import com.homelab.household.domain.exception.NameTakenException
import com.homelab.household.domain.exception.PinLockedException
import com.homelab.household.domain.model.Invite
import com.homelab.household.domain.model.ResetCode
import com.homelab.household.domain.model.User
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

class MembersRepositoryTest {

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
        MembersRepositoryImpl(
            HttpClient(engine) { install(ContentNegotiation) { json(json) } },
            tokenStorage,
            baseUrl = DEFAULT_BASE_URL
        )

    private fun assertSameJson(expected: String, actual: String?) =
        assertEquals(Json.parseToJsonElement(expected), Json.parseToJsonElement(actual ?: "null"))

    @Test
    fun the_household_member_list_comes_back_as_users() = runTest {
        val engine = MockEngine { request ->
            if (request.url.encodedPath == "/api/v1/users") {
                respondJson("[$emmaJson]")
            } else {
                respond("Not Found", HttpStatusCode.NotFound)
            }
        }

        val members = repo(engine).listHouseholdMembers()

        assertEquals(1, members.size)
        assertEquals(User(id = "emma", fullName = "Emma", isAdmin = true, isActive = true, personalSpaceId = "sp-1", avatarColor = "#3C6E4E", createdAt = "2026-09-13T00:00:00Z"), members.first())
    }

    @Test
    fun listing_household_members_with_the_hub_unreachable_throws_server_offline() = runTest {
        val engine = MockEngine { throw IOException("Connection refused") }

        assertFailsWith<ServerOfflineException> { repo(engine).listHouseholdMembers() }
    }

    @Test
    fun creating_an_invite_sends_the_name_and_admin_flag() = runTest {
        var sent: String? = null
        val engine = MockEngine { request ->
            if (request.url.encodedPath == "/api/v1/invites") {
                sent = (request.body as TextContent).text
                respondJson(
                    """{"code": "482913", "invited_name": "Liam", "is_admin": false, "expires_at": "2026-09-16T00:15:00Z", "expires_in_seconds": 900}""",
                    HttpStatusCode.Created
                )
            } else {
                respond("Not Found", HttpStatusCode.NotFound)
            }
        }

        val invite = repo(engine).createInvite("Liam", false)

        assertEquals(Invite(code = "482913", invitedName = "Liam", isAdmin = false, expiresInSeconds = 900), invite)
        assertSameJson("""{"invited_name": "Liam", "is_admin": false}""", sent)
    }

    @Test
    fun creating_an_invite_with_the_hub_unreachable_throws_server_offline() = runTest {
        val engine = MockEngine { throw IOException("Connection refused") }

        assertFailsWith<ServerOfflineException> { repo(engine).createInvite("Liam", false) }
    }

    @Test
    fun approving_a_pin_reset_sends_the_approvers_pin() = runTest {
        var sent: String? = null
        val engine = MockEngine { request ->
            if (request.url.encodedPath == "/api/v1/users/liam/pin-resets") {
                sent = (request.body as TextContent).text
                respondJson(
                    """{"code": "738291", "expires_at": "2026-09-16T00:15:00Z", "expires_in_seconds": 900}""",
                    HttpStatusCode.Created
                )
            } else {
                respond("Not Found", HttpStatusCode.NotFound)
            }
        }

        val resetCode = repo(engine).approvePinReset("liam", "111111")

        assertEquals(ResetCode(code = "738291", expiresInSeconds = 900), resetCode)
        assertSameJson("""{"pin": "111111"}""", sent)
    }

    @Test
    fun approving_a_pin_reset_with_a_wrong_own_pin_says_how_many_attempts_are_left() = runTest {
        val engine = MockEngine {
            respondJson("""{"detail": "Wrong PIN", "code": "wrong_pin", "attempts_left": 2}""", HttpStatusCode.Forbidden)
        }

        val e = assertFailsWith<WrongPinException> { repo(engine).approvePinReset("liam", "000000") }
        assertEquals(2, e.attemptsLeft)
    }

    @Test
    fun approving_a_pin_reset_when_locked_says_how_long_to_wait() = runTest {
        val engine = MockEngine {
            respondJson("""{"detail": "Too many wrong PINs", "code": "pin_locked", "retry_after_seconds": 30}""", HttpStatusCode.TooManyRequests)
        }

        val e = assertFailsWith<PinLockedException> { repo(engine).approvePinReset("liam", "000000") }
        assertEquals(30, e.retryAfterSeconds)
    }

    @Test
    fun changing_a_pin_sends_both_pins_and_keeps_the_new_token() = runTest {
        var sent: String? = null
        val engine = MockEngine { request ->
            if (request.url.encodedPath == "/api/v1/users/me/pin") {
                sent = (request.body as TextContent).text
                respondJson("""{"access_token": "new-token", "token_type": "bearer", "user": $emmaJson}""")
            } else {
                respond("Not Found", HttpStatusCode.NotFound)
            }
        }
        val tokenStorage = InMemoryTokenStorage().apply { saveTokens("old-token") }

        repo(engine, tokenStorage).changePin("111111", "222222")

        assertEquals("new-token", tokenStorage.getAccessToken())
        assertSameJson("""{"current_pin": "111111", "new_pin": "222222"}""", sent)
    }

    @Test
    fun changing_a_pin_with_the_wrong_current_pin_says_how_many_attempts_are_left() = runTest {
        val engine = MockEngine {
            respondJson("""{"detail": "Wrong PIN", "code": "wrong_pin", "attempts_left": 1}""", HttpStatusCode.Forbidden)
        }
        val tokenStorage = InMemoryTokenStorage().apply { saveTokens("old-token") }

        val e = assertFailsWith<WrongPinException> { repo(engine, tokenStorage).changePin("000000", "222222") }
        assertEquals(1, e.attemptsLeft)
        assertEquals("old-token", tokenStorage.getAccessToken())
    }

    @Test
    fun removing_a_member_asks_the_hub() = runTest {
        val engine = MockEngine { request ->
            if (request.url.encodedPath == "/api/v1/users/liam") {
                respondJson("""{"message": "Member removed from the household"}""")
            } else {
                respond("Not Found", HttpStatusCode.NotFound)
            }
        }

        repo(engine).removeMember("liam")
    }

    @Test
    fun removing_the_sole_admin_says_so() = runTest {
        val engine = MockEngine {
            respondJson("""{"detail": "The only admin can't be removed.", "code": "sole_admin"}""", HttpStatusCode.Conflict)
        }

        assertFailsWith<SoleAdminException> { repo(engine).removeMember("emma") }
    }

    @Test
    fun leaving_the_household_sends_the_members_own_pin() = runTest {
        var sent: String? = null
        val engine = MockEngine { request ->
            if (request.url.encodedPath == "/api/v1/users/me") {
                sent = (request.body as TextContent).text
                respondJson("""{"message": "You have left the household"}""")
            } else {
                respond("Not Found", HttpStatusCode.NotFound)
            }
        }

        repo(engine).leaveHousehold("111111")

        assertSameJson("""{"pin": "111111"}""", sent)
    }

    @Test
    fun leaving_with_a_wrong_pin_says_how_many_attempts_are_left() = runTest {
        val engine = MockEngine {
            respondJson("""{"detail": "Wrong PIN", "code": "wrong_pin", "attempts_left": 2}""", HttpStatusCode.Forbidden)
        }

        val e = assertFailsWith<WrongPinException> { repo(engine).leaveHousehold("000000") }
        assertEquals(2, e.attemptsLeft)
    }

    @Test
    fun the_sole_admin_cannot_leave() = runTest {
        val engine = MockEngine {
            respondJson("""{"detail": "The only admin can't leave.", "code": "sole_admin"}""", HttpStatusCode.Conflict)
        }

        assertFailsWith<SoleAdminException> { repo(engine).leaveHousehold("111111") }
    }

    @Test
    fun leaving_with_the_hub_unreachable_throws_server_offline() = runTest {
        val engine = MockEngine { throw IOException("Connection refused") }

        assertFailsWith<ServerOfflineException> { repo(engine).leaveHousehold("111111") }
    }

    @Test
    fun inviting_a_name_the_household_already_has_says_so() = runTest {
        val engine = MockEngine {
            respondJson(
                """{"detail": "Someone in the household already has that name.", "code": "name_taken"}""",
                HttpStatusCode.Conflict
            )
        }

        assertFailsWith<NameTakenException> { repo(engine).createInvite("Emma", isAdmin = false) }
    }
}
