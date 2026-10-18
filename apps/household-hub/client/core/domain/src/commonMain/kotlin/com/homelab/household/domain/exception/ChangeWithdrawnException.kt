package com.homelab.household.domain.exception

/**
 * Answering a change whose sentence its asker took back while it waited. [message] is the hub's own
 * words, naming them: "Emma took this back."
 */
class ChangeWithdrawnException(
    message: String,
) : DomainException(message)
