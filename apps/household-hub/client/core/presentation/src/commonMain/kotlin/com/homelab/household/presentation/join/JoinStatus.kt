package com.homelab.household.presentation.join

/** Where joining the household stands. */
sealed interface JoinStatus {
    data object Idle : JoinStatus

    /** The name, PIN and colour are with the hub. */
    data object Joining : JoinStatus

    /** The code was used or ran out while the form was being filled in. */
    data object Expired : JoinStatus

    data object Unreachable : JoinStatus
    data object Failed : JoinStatus
}
