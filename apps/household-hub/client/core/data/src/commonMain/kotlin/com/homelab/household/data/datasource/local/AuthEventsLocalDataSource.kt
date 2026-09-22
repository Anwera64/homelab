package com.homelab.household.data.datasource.local

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The one thing about this phone's sign-in that is an event rather than state: the hub has stopped
 * accepting its token.
 *
 * Who is signed in is not here. That is kept on the phone by [StoredSessionLocalDataSource], beside
 * the token it belongs to, so that a cold start with no hub to ask still has a name and a colour to
 * draw. A copy in memory would only be a second place for it to go stale.
 *
 * By the time this is raised the token and the member are already forgotten: `signOutOnUnauthorized`
 * clears the storage and then announces it. Nothing here needs a lock — `tryEmit` on a buffered flow
 * is atomic, and there is no state left to guard.
 */
class AuthEventsLocalDataSource {

    /**
     * Nothing is replayed: a sign-out is an event, and a screen collecting later must not be sent
     * back to "Who's here?" for an old one.
     */
    private val signedOut = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    fun observeSignedOut(): Flow<Unit> = signedOut.asSharedFlow()

    /** The hub stopped accepting this phone's token. */
    fun raiseSignedOut() {
        signedOut.tryEmit(Unit)
    }
}
