package com.homelab.household.data.datasource.local

import com.homelab.household.data.dto.UserReadDto
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Who is signed in on this phone, for as long as the app is running — and the one place that says
 * they no longer are.
 *
 * It holds the DTO rather than a `User`: DTOs below the repository, domain models above it, so the
 * cache cannot quietly become a second place where mapping happens.
 *
 * Nothing here needs a lock. The cached member is a single reference behind a [MutableStateFlow],
 * whose writes are already atomic, and the sign-out is an emission rather than state. That is the
 * point of pulling it out of the repository: what is shared between coroutines is this file, and it
 * is small enough to see all of at once.
 */
class AuthSessionLocalDataSource {

    private val cachedUser = MutableStateFlow<UserReadDto?>(null)

    /**
     * Nothing is replayed: a sign-out is an event, and a screen collecting later must not be sent
     * back to "Who's here?" for an old one.
     */
    private val signedOut = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    fun observeCurrentUser(): Flow<UserReadDto?> = cachedUser.asStateFlow()

    /** Who is signed in as far as this phone knows, without asking the hub. */
    fun currentUser(): UserReadDto? = cachedUser.value

    fun cacheCurrentUser(user: UserReadDto?) {
        cachedUser.value = user
    }

    fun forgetCurrentUser() {
        cachedUser.value = null
    }

    fun observeSignedOut(): Flow<Unit> = signedOut.asSharedFlow()

    /**
     * The hub stopped accepting this phone's token. Forgetting who was signed in happens here
     * rather than in whoever collects [observeSignedOut], so it happens even when nobody is.
     */
    fun raiseSignedOut() {
        forgetCurrentUser()
        signedOut.tryEmit(Unit)
    }
}
