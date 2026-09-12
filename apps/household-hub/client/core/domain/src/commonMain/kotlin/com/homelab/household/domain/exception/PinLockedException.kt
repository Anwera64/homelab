package com.homelab.household.domain.exception

/** Too many wrong PINs: the hub won't check another one for [retryAfterSeconds]. */
class PinLockedException(val retryAfterSeconds: Int) : DomainException("PIN locked for $retryAfterSeconds seconds")
