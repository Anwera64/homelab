package com.homelab.household.domain.exception

class ServerOfflineException(
    message: String = "Homelab server is unreachable",
    val isReachable: Boolean = false,
    cause: Throwable? = null
) : DomainException(message, cause)
