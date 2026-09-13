package com.homelab.household.domain.usecase

interface ApproveToolProposalUseCase {
    suspend operator fun invoke(
        sessionId: String,
        toolCallId: String,
        approved: Boolean,
        modifiedArguments: Map<String, Any?>? = null
    ): Boolean
}
