package com.homelab.household.presentation.removemember

/** Where removing a member stands. */
sealed interface RemoveMemberStatus {
    data object Idle : RemoveMemberStatus

    data object Removing : RemoveMemberStatus

    data object Unreachable : RemoveMemberStatus

    data object Failed : RemoveMemberStatus
}
