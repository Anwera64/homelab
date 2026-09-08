package com.homelab.household.data.remote

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.utils.io.errors.IOException
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NetworkExceptionHelperTest {

    @Test
    fun identifies_ktor_io_and_socket_exceptions_as_offline() {
        assertTrue(NetworkExceptionHelper.isNetworkOfflineException(IOException("IO failed")))
        assertTrue(NetworkExceptionHelper.isNetworkOfflineException(SocketTimeoutException("Timeout")))
        assertTrue(NetworkExceptionHelper.isNetworkOfflineException(ConnectTimeoutException("Connect timeout")))
        assertTrue(NetworkExceptionHelper.isNetworkOfflineException(HttpRequestTimeoutException("http://localhost", 1000L)))
    }

    @Test
    fun identifies_connection_refused_message_as_offline() {
        val runtimeException = RuntimeException("Connection refused by peer")
        assertTrue(NetworkExceptionHelper.isNetworkOfflineException(runtimeException))
    }

    @Test
    fun does_not_classify_business_or_serialization_errors_as_offline() {
        assertFalse(NetworkExceptionHelper.isNetworkOfflineException(IllegalStateException("Invalid state")))
        assertFalse(NetworkExceptionHelper.isNetworkOfflineException(IllegalArgumentException("Bad input")))
    }
}
