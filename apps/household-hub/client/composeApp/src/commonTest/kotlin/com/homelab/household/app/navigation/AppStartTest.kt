package com.homelab.household.app.navigation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.waitUntilExactlyOneExists
import com.homelab.household.app.testing.TestApp
import com.homelab.household.app.testing.runScreenTest
import com.homelab.household.data.local.InMemoryTokenStorage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Where the app opens, decided on the phone before anything is drawn. `AppNavHostTest` hands the
 * host a back stack; this is the one the host makes for itself, so it runs over the real graph —
 * the stored session is read through Koin — with the screens still stubbed out.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalAtomicApi::class)
class AppStartTest {

    private val hubCalls = AtomicInt(0)
    private val hub = MockEngine {
        hubCalls.incrementAndFetch()
        respond("Nothing on the way to home should wait for the hub", HttpStatusCode.NotImplemented)
    }

    private fun hubThatRenews() = MockEngine { request ->
        hubCalls.incrementAndFetch()
        if (request.url.encodedPath == "/api/v1/auth/refresh") {
            respond(
                """{"access_token":"fresh-token","token_type":"bearer","user":{"id":"emma","full_name":"Emma",
                    "avatar_color":"#3C6E4E","is_admin":true,"is_active":true,"created_at":"2026-09-15T00:00:00Z"}}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        } else {
            respond("", HttpStatusCode.NotFound)
        }
    }

    @Test
    fun a_phone_still_signed_in_opens_on_home_without_waiting_for_the_hub() {
        val tokens = InMemoryTokenStorage().apply { saveTokens("token-from-last-time") }

        runScreenTest {
            setContent { TestApp(hub, tokenStorage = tokens) { AppNavHost(screens = StubScreens()) } }

            waitUntilExactlyOneExists(hasText(StubScreens.HOME), timeoutMillis = WAIT_MILLIS)
        }
    }

    @Test
    fun a_phone_still_signed_in_renews_its_token_once() {
        val tokens = InMemoryTokenStorage().apply { saveTokens("token-from-last-time") }

        runScreenTest {
            setContent { TestApp(hubThatRenews(), tokenStorage = tokens) { AppNavHost(screens = StubScreens()) } }

            waitUntil(timeoutMillis = WAIT_MILLIS) { tokens.getAccessToken() == "fresh-token" }
        }

        assertEquals(1, hubCalls.load())
    }

    @Test
    fun a_phone_whose_token_the_hub_no_longer_accepts_goes_to_who_is_here() {
        val tokens = InMemoryTokenStorage().apply { saveTokens("revoked-token") }
        val refusing = MockEngine { respond("""{"detail":"Invalid or expired token."}""", HttpStatusCode.Unauthorized) }

        runScreenTest {
            setContent { TestApp(refusing, tokenStorage = tokens) { AppNavHost(screens = StubScreens()) } }

            waitUntilExactlyOneExists(hasText(StubScreens.SIGN_IN), timeoutMillis = WAIT_MILLIS)
        }

        assertEquals(null, tokens.getAccessToken())
    }

    @Test
    fun a_phone_with_nobody_signed_in_opens_on_launch() {
        runScreenTest {
            setContent { TestApp(hub, tokenStorage = InMemoryTokenStorage()) { AppNavHost(screens = StubScreens()) } }

            waitUntilExactlyOneExists(hasText(StubScreens.LAUNCH), timeoutMillis = WAIT_MILLIS)
        }
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}
