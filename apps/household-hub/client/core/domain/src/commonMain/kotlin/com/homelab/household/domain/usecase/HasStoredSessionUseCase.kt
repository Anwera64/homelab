package com.homelab.household.domain.usecase

/**
 * Whether this phone kept a session from last time — answered from the phone alone, so the app
 * can pick where it opens before anything is drawn.
 */
fun interface HasStoredSessionUseCase {
    operator fun invoke(): Boolean
}
