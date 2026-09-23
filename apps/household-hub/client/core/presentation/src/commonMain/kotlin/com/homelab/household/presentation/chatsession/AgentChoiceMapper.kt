package com.homelab.household.presentation.chatsession

import com.homelab.household.domain.model.AgentPersonality
import com.homelab.household.domain.model.User

/**
 * An agent as the picker shows it.
 *
 * [myId] is whoever is signed in, or null when that could not be read; [membersById] may be empty
 * when the members could not be listed. Either way the card still draws, just without a name it
 * cannot vouch for.
 */
fun AgentPersonality.toAgentChoice(
    myId: String?,
    membersById: Map<String, User>,
): AgentChoice =
    AgentChoice(
        id = id,
        name = name,
        avatar = avatar,
        tagline = description,
        owner =
            when {
                isBuiltin -> {
                    AgentOwner.BuiltIn
                }

                myId != null && ownerId == myId -> {
                    AgentOwner.Yours
                }

                else -> {
                    membersById[ownerId]
                        ?.fullName
                        ?.substringBefore(' ')
                        ?.takeIf { it.isNotBlank() }
                        ?.let(AgentOwner::Member)
                        ?: AgentOwner.Unknown
                }
            },
        tools = toolPermissions,
    )
