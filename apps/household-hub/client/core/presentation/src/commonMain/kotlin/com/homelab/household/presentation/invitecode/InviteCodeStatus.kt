package com.homelab.household.presentation.invitecode

/** Where entering an invite code stands. */
sealed interface InviteCodeStatus {
    data object Idle : InviteCodeStatus

    /** The code is with the hub. */
    data object Checking : InviteCodeStatus

    /** Continue was tapped before the sixth character: the button is never disabled, so it says so. */
    data object Incomplete : InviteCodeStatus

    /** Unknown, used or expired — the hub answers alike for all three, so a guess learns nothing. */
    data object Invalid : InviteCodeStatus

    /** Too many wrong codes across the whole hub; [secondsLeft] counts down. */
    data class Locked(val secondsLeft: Int) : InviteCodeStatus

    data object Unreachable : InviteCodeStatus
    data object Failed : InviteCodeStatus
}
