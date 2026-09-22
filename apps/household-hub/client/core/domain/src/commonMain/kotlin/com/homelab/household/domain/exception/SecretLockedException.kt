package com.homelab.household.domain.exception

class SecretLockedException(
    message: String = "Conversation is locked behind Secret Mode authentication",
) : DomainException(message)
