package com.homelab.household.domain.exception

/** The hub refused the PIN. [attemptsLeft] is how many more misses it allows before a wait. */
class WrongPinException(
    val attemptsLeft: Int,
) : DomainException("Wrong PIN, $attemptsLeft attempts left")
