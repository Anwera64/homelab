package com.homelab.household.data.network

import com.homelab.household.data.datasource.local.InMemorySessionStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

/**
 * A 401 means the hub no longer accepts this phone's token — a PIN changed elsewhere, or the member
 * was removed. The phone forgets the token and says so once, so the app can go back to "Who's here?".
 * Sign-in's own 401 is a wrong PIN, and a wrong PIN while signed in is a 403: neither signs out.
 */
class SignOutOnUnauthorizedTest {

    private val hub = "https://hub.test.local"

    private fun client(status: HttpStatusCode, storage: InMemorySessionStorage, onSignedOut: () -> Unit) =
        HttpClient(MockEngine { respond("""{"detail":"no"}""", status, headersOf(HttpHeaders.ContentType, "application/json")) }) {
            signOutOnUnauthorized(storage, onSignedOut)
        }

    @Test
    fun `GIVEN a token kept on this phone WHEN a signed-in call comes back 401 THEN the token is forgotten and the sign-out is announced once`() = runTest {
        // GIVEN
        val tokens = InMemorySessionStorage().apply { saveTokens("revoked-token") }
        var signedOut = 0

        // WHEN
        client(HttpStatusCode.Unauthorized, tokens) { signedOut++ }.get("$hub/api/v1/spaces/shared")

        // THEN
        assertNull(tokens.getAccessToken())
        assertEquals(1, signedOut)
    }

    @Test
    fun `GIVEN someone else is signed in on this phone WHEN sign-in refuses a PIN with 401 THEN their token is left alone`() = runTest {
        // GIVEN
        val tokens = InMemorySessionStorage().apply { saveTokens("someone-elses-token") }
        var signedOut = 0

        // WHEN
        client(HttpStatusCode.Unauthorized, tokens) { signedOut++ }.post("$hub/api/v1/auth/login")

        // THEN
        assertEquals("someone-elses-token", tokens.getAccessToken())
        assertEquals(0, signedOut)
    }

    @Test
    fun `GIVEN a member changing their PIN WHEN the hub refuses the current one with 403 THEN they stay signed in`() = runTest {
        // GIVEN
        val tokens = InMemorySessionStorage().apply { saveTokens("good-token") }
        var signedOut = 0

        // WHEN
        client(HttpStatusCode.Forbidden, tokens) { signedOut++ }.post("$hub/api/v1/users/me/pin")

        // THEN
        assertEquals("good-token", tokens.getAccessToken())
        assertEquals(0, signedOut)
    }

    @Test
    fun `GIVEN no token kept on this phone WHEN a call comes back 401 THEN nobody is signed out`() = runTest {
        // GIVEN
        var signedOut = 0

        // WHEN
        client(HttpStatusCode.Unauthorized, InMemorySessionStorage()) { signedOut++ }.get("$hub/api/v1/spaces/shared")

        // THEN
        assertEquals(0, signedOut)
    }

    @Test
    fun `GIVEN the calls the hub answers without a token WHEN each is checked THEN only those read as public`() {
        // GIVEN
        val public = listOf(
            "/api/v1/auth/login",
            "/api/v1/auth/register-initial",
            "/api/v1/auth/members",
            "/api/v1/auth/status",
            "/api/v1/health",
            "/api/v1/invites/K7M2QP",
            "/api/v1/invites/K7M2QP/redeem",
            "/api/v1/auth/pin-resets/K7M2QP/redeem",
        )
        val signedIn = listOf("/api/v1/invites", "/api/v1/users/me/pin", "/api/v1/auth/refresh", "/api/v1/auth/me")

        // WHEN / THEN
        public.forEach { path -> assertEquals(true, PublicEndpoints.isPublic("$hub$path"), path) }
        signedIn.forEach { path -> assertEquals(false, PublicEndpoints.isPublic("$hub$path"), path) }
    }
}
