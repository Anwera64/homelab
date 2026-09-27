package com.homelab.household.domain.exception

/**
 * The hub answered, but it could not reach the calendar server. Not [ServerOfflineException]: the
 * hub is fine, and the fix is the server address, not the member's connection.
 */
class CalendarUnreachableException(
    message: String = "The hub couldn't reach the calendar",
) : DomainException(message)
