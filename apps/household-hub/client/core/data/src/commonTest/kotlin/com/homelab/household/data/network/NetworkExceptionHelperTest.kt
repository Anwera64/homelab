package com.homelab.household.data.network

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.utils.io.errors.IOException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkExceptionHelperTest {

    @Test
    fun `GIVEN the failures Ktor raises when it cannot reach a server WHEN each is classified THEN all of them read as offline`() {
        // GIVEN
        val failures = listOf(
            IOException("IO failed"),
            SocketTimeoutException("Timeout"),
            ConnectTimeoutException("Connect timeout"),
            HttpRequestTimeoutException("http://localhost", 1000L),
        )

        // WHEN / THEN
        failures.forEach { failure ->
            assertTrue(NetworkExceptionHelper.isNetworkOfflineException(failure), failure::class.simpleName)
        }
    }

    @Test
    fun `GIVEN a platform failure that only says connection refused in its message WHEN it is classified THEN it reads as offline`() {
        // GIVEN
        val failure = RuntimeException("Connection refused by peer")

        // WHEN
        val offline = NetworkExceptionHelper.isNetworkOfflineException(failure)

        // THEN
        assertTrue(offline)
    }

    @Test
    fun `GIVEN a programming or parsing failure WHEN it is classified THEN it does not read as offline`() {
        // GIVEN
        val failures = listOf(IllegalStateException("Invalid state"), IllegalArgumentException("Bad input"))

        // WHEN / THEN
        failures.forEach { failure ->
            assertFalse(NetworkExceptionHelper.isNetworkOfflineException(failure), failure::class.simpleName)
        }
    }
}
