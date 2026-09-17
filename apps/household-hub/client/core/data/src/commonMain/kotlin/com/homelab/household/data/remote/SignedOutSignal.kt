package com.homelab.household.data.remote

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Raised when the hub stopped accepting this phone's token. Shared between the HTTP client, which
 * notices, and the auth repository, which passes it on. Nothing is replayed: a sign-out is an event,
 * and a screen collecting later must not be sent back to "Who's here?" for an old one.
 */
class SignedOutSignal {
    private val raised = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val events: SharedFlow<Unit> = raised.asSharedFlow()

    fun raise() {
        raised.tryEmit(Unit)
    }
}
