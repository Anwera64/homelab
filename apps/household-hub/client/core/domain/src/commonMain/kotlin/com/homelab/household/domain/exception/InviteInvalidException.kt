package com.homelab.household.domain.exception

/** The invite or reset code is unknown, already used, or expired. */
class InviteInvalidException(
    message: String = "That code isn't valid",
) : DomainException(message)
