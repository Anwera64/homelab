package com.homelab.household.domain.exception

/** The hub has no Google sign-in set up, so no Google calendar can be connected until its owner adds one. */
class GoogleSignInUnavailableException(
    message: String = "This hub isn't set up for Google sign-in",
) : DomainException(message)
