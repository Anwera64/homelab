package com.homelab.household.domain.exception

/** First run on a hub that already has members: someone else set it up first. */
class HubAlreadySetUpException(message: String = "The hub already has members") : DomainException(message)
