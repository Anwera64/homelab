package com.homelab.household.domain.exception

/**
 * The hub refused a message because the conversation's last answer is waiting on a card.
 *
 * Nothing was sent and nothing was declined for the member: what they typed stays theirs, and the
 * card is what to answer first.
 */
class ApprovalPendingException(
    message: String = "Answer the card above before sending another message",
) : DomainException(message)
