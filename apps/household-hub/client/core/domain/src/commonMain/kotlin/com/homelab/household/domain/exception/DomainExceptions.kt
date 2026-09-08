package com.homelab.household.domain.exception

open class DomainException(message: String, cause: Throwable? = null) : Exception(message, cause)

class ValidationException(message: String) : DomainException(message)

class UnauthorizedException(message: String = "Unauthorized") : DomainException(message)

class ForbiddenException(message: String = "Forbidden") : DomainException(message)

class NotFoundException(message: String = "Resource not found") : DomainException(message)

class SessionConflictException(message: String = "Session is currently processing another message") : DomainException(message)

class ServerOfflineException(
    message: String = "Homelab server is unreachable",
    val isReachable: Boolean = false,
    cause: Throwable? = null
) : DomainException(message, cause)

class UpstreamGatewayException(
    val statusCode: Int,
    message: String = "Upstream server returned error $statusCode"
) : DomainException(message)

class SecretLockedException(message: String = "Conversation is locked behind Secret Mode authentication") : DomainException(message)
