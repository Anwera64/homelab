package com.homelab.household.domain.usecase

import com.homelab.household.domain.repository.AuthRepository

/**
 * Whether this phone kept a session from last time — answered from the phone alone, so the app
 * can pick where it opens before anything is drawn.
 */
class HasStoredSessionUseCase(private val authRepository: AuthRepository) {
    operator fun invoke(): Boolean = authRepository.hasStoredSession()
}
