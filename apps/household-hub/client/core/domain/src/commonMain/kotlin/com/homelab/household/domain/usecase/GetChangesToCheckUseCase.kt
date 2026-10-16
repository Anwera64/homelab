package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.ChangesToCheck

/** Changes to check: yours to answer, then the ones from your chats waiting for someone else. */
fun interface GetChangesToCheckUseCase {
    suspend operator fun invoke(): ChangesToCheck
}
