package com.homelab.household.data.remote

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.utils.io.errors.IOException

object NetworkExceptionHelper {
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
