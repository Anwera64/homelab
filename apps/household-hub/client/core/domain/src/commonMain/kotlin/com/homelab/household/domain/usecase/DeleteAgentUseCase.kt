package com.homelab.household.domain.usecase

fun interface DeleteAgentUseCase {
    suspend operator fun invoke(agentId: String)
}
