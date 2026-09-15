package com.homelab.household.domain.usecase

import kotlinx.coroutines.flow.Flow

/** The hub stopped accepting this phone's token: a PIN changed elsewhere, or the member was removed. */
fun interface ObserveSignedOutUseCase {
    operator fun invoke(): Flow<Unit>
}
