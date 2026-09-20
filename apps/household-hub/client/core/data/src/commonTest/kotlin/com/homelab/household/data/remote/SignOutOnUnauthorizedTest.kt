package com.homelab.household.data.remote

import com.homelab.household.data.local.InMemoryTokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A 401 means the hub no longer accepts this phone's token — a PIN changed elsewhere, or the member
 * was removed. The phone forgets the token and says so once, so the app can go back to "Who's here?".
 * Sign-in's own 401 is a wrong PIN, and a wrong PIN while signed in is a 403: neither signs out.
 */
class SignOutOnUnauthorizedTest {

    private val hub = "https://hub.test.local"

    private fun client(status: HttpStatusCode, tokenStorage: InMemoryTokenStorage, onSignedOut: () -> Unit) =
        HttpClient(MockEngine { respond("""{"detail":"no"}""", status, headersOf(HttpHeaders.ContentType, "application/json")) }) {
            signOutOnUnauthorized(tokenStorage, onSignedOut)
        }

    @Test
    fun a_401_to_a_signed_in_call_forgets_the_token_and_says_so_once() = runTest {
        val tokens = InMemoryTokenStorage().apply { saveTokens("revoked-token") }
        var signedOut = 0

        client(HttpStatusCode.Unauthorized, tokens) { signedOut++ }.get("$hub/api/v1/spaces/shared")

        assertNull(tokens.getAccessToken())
        assertEquals(1, signedOut)
    }

    @Test
    fun a_wrong_pin_at_sign_in_is_not_a_sign_out() = runTest {
        val tokens = InMemoryTokenStorage().apply { saveTokens("someone-elses-token") }
        var signedOut = 0

        client(HttpStatusCode.Unauthorized, tokens) { signedOut++ }.post("$hub/api/v1/auth/login")

        assertEquals("someone-elses-token", tokens.getAccessToken())
        assertEquals(0, signedOut)
    }

    @Test
    fun a_wrong_pin_while_signed_in_is_not_a_sign_out() = runTest {
        val tokens = InMemoryTokenStorage().apply { saveTokens("good-token") }
        var signedOut = 0

        client(HttpStatusCode.Forbidden, tokens) { signedOut++ }.post("$hub/api/v1/users/me/pin")

        assertEquals("good-token", tokens.getAccessToken())
        assertEquals(0, signedOut)
    }

    @Test
    fun a_401_with_no_token_kept_signs_nobody_out() = runTest {
        var signedOut = 0

        client(HttpStatusCode.Unauthorized, InMemoryTokenStorage()) { signedOut++ }.get("$hub/api/v1/spaces/shared")

        assertEquals(0, signedOut)
    }

    @Test
    fun calls_that_need_no_token_are_named_once() {
        listOf(
            "/api/v1/auth/login",
            "/api/v1/auth/register-initial",
            "/api/v1/auth/members",
            "/api/v1/auth/status",
            "/api/v1/health",
            "/api/v1/invites/K7M2QP",
            "/api/v1/invites/K7M2QP/redeem",
            "/api/v1/auth/pin-resets/K7M2QP/redeem",
        ).forEach { path -> assertEquals(true, PublicEndpoints.isPublic("$hub$path"), path) }

        listOf("/api/v1/invites", "/api/v1/users/me/pin", "/api/v1/auth/refresh", "/api/v1/auth/me")
            .forEach { path -> assertEquals(false, PublicEndpoints.isPublic("$hub$path"), path) }
    }
}
