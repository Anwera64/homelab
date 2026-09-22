package com.homelab.household.domain.exception

/** Someone in the household already has this name. */
class NameTakenException(
    message: String = "That name is already taken",
) : DomainException(message)
