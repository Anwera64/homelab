package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.ToolApproval

/** Every write agents can do, and whether they do it for the member without asking. */
fun interface GetToolApprovalsUseCase {
    suspend operator fun invoke(): List<ToolApproval>
}
