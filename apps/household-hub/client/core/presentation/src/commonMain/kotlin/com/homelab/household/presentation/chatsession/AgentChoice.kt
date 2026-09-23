package com.homelab.household.presentation.chatsession

/**
 * One agent a new chat could start with, as the picker shows it.
 *
 * [tools] keep the backend's names; turning them into words for a person is the screen's job, as
 * it is for the turn trail (design notes §2).
 */
data class AgentChoice(
    val id: String,
    val name: String,
    val avatar: String,
    val tagline: String,
    val owner: AgentOwner,
    val tools: List<String>,
)

/** Whose agent this is — the chip beside its name. */
sealed interface AgentOwner {
    data object BuiltIn : AgentOwner

    data object Yours : AgentOwner

    /** Someone else in the household made it; named by their first name. */
    data class Member(
        val firstName: String,
    ) : AgentOwner

    /** The owner could not be named, so the card says nothing rather than something wrong. */
    data object Unknown : AgentOwner
}
