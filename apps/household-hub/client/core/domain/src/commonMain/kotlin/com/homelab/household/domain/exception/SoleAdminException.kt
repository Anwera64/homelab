package com.homelab.household.domain.exception

/** The only admin can't be removed or leave: nothing could take their place. */
class SoleAdminException(message: String = "The only admin can't do that") : DomainException(message)
