package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.InvitePreview

/** Before choosing a PIN, the joiner sees who invited them and the name they were invited as. */
fun interface LookUpInviteUseCase {
    suspend operator fun invoke(code: String): InvitePreview
}
