package com.homelab.household.app.navigation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** The session as navigation sees it, with the hub's sign-out in the test's hands. */
class StubSession : AppSession {
    private val hubSignsOut = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    var renewals = 0
        private set

    override val signedOut: Flow<Unit> = hubSignsOut

    override suspend fun renew() {
        renewals++
    }

    fun hubSignsThisPhoneOut() {
        hubSignsOut.tryEmit(Unit)
    }
}
