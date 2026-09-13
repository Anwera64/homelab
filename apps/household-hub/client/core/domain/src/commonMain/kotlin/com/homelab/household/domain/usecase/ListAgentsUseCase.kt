package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.AgentPersonality

fun interface ListAgentsUseCase {
    suspend operator fun invoke(): List<AgentPersonality>
}
