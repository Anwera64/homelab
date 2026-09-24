package com.homelab.household.data.network

/**
 * The hub no longer holds the turn this phone was following, so there is nothing to resume.
 *
 * Not a failure: the turn finished and was let go, or another has started, and either way the
 * answer — if there is one — is in the conversation. It stays inside the data layer because only
 * the repository acts on it, by reading the conversation back instead.
 */
class TurnGoneException(
    message: String = "The hub no longer holds this turn",
) : Exception(message)
