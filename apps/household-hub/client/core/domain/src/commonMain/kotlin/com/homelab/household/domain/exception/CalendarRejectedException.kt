package com.homelab.household.domain.exception

/** The calendar server answered and refused the account or password. Almost always the password. */
class CalendarRejectedException(
    message: String = "The calendar refused those details",
) : DomainException(message)
