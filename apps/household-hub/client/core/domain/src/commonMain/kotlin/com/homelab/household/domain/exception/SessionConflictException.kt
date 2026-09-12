package com.homelab.household.domain.exception

class SessionConflictException(message: String = "Session is currently processing another message") : DomainException(message)
