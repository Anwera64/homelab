package com.homelab.household.domain.exception

/** Too many wrong codes: the hub won't check another one for [retryAfterSeconds]. */
class CodeGuessesLockedException(
    val retryAfterSeconds: Int,
) : DomainException("Too many attempts, locked for $retryAfterSeconds seconds")
