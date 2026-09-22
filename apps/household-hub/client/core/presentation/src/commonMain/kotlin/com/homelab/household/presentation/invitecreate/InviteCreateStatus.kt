package com.homelab.household.presentation.invitecreate

/** Where making an invite stands. */
sealed interface InviteCreateStatus {
    data object Idle : InviteCreateStatus

    data object Creating : InviteCreateStatus

    /** The code has run out; a new one is a tap away. */
    data object Expired : InviteCreateStatus

    data object Unreachable : InviteCreateStatus

    data object Failed : InviteCreateStatus
}
