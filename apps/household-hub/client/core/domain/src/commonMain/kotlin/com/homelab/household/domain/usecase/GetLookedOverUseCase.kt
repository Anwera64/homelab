package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.LookedOver

/** A chat's sheet, "Remembered from this chat". */
fun interface GetLookedOverUseCase {
    suspend operator fun invoke(sessionId: String): LookedOver
}
