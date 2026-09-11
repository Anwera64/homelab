package com.homelab.household.data.remote

import com.homelab.household.domain.exception.ServerOfflineException
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.utils.io.errors.IOException

object NetworkExceptionHelper {
    /** Rethrows network failures as [ServerOfflineException]; anything else is rethrown unchanged. */
    fun rethrowAsDomain(e: Throwable): Nothing {
        if (isNetworkOfflineException(e)) {
            throw ServerOfflineException(message = e.message ?: "Server is offline", cause = e)
        }
        throw e
    }

    fun isNetworkOfflineException(e: Throwable): Boolean {
        if (e is IOException ||
            e is SocketTimeoutException ||
            e is ConnectTimeoutException ||
            e is HttpRequestTimeoutException
        ) {
            return true
        }

        var current: Throwable? = e
        while (current != null) {
            val name = current::class.simpleName ?: ""
            val msg = current.message ?: ""
            if (name.contains("ConnectException", ignoreCase = true) ||
                name.contains("SocketException", ignoreCase = true) ||
                name.contains("UnknownHostException", ignoreCase = true) ||
                name.contains("UnresolvedAddressException", ignoreCase = true) ||
                name.contains("TimeoutException", ignoreCase = true) ||
                msg.contains("Connection refused", ignoreCase = true) ||
                msg.contains("Failed to connect", ignoreCase = true) ||
                msg.contains("Network unreachable", ignoreCase = true) ||
                msg.contains("Unable to resolve host", ignoreCase = true)
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }
}
