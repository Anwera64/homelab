package com.homelab.household.domain.exception

class UnauthorizedException(
    message: String = "Unauthorized",
) : DomainException(message)
