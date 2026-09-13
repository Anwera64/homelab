package com.homelab.household.app.navigation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.waitUntilExactlyOneExists
import com.homelab.household.app.testing.TestApp
import com.homelab.household.app.testing.runScreenTest
import com.homelab.household.data.local.InMemoryTokenStorage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
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
        respond("Nothing on the way to home should ask the hub", HttpStatusCode.NotImplemented)
    }

    @Test
    fun a_phone_still_signed_in_opens_on_home_without_asking_the_hub() {
        val tokens = InMemoryTokenStorage().apply { saveTokens("token-from-last-time") }

        runScreenTest {
            setContent { TestApp(hub, tokenStorage = tokens) { AppNavHost(screens = StubScreens()) } }

            waitUntilExactlyOneExists(hasText(StubScreens.HOME), timeoutMillis = WAIT_MILLIS)
        }

        assertEquals(0, hubCalls.load())
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
