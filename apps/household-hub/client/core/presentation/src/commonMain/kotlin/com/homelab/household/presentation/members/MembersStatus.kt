package com.homelab.household.presentation.members

/** Where the members list stands. */
sealed interface MembersStatus {
    data object Loading : MembersStatus
    data object Ready : MembersStatus
    data object Unreachable : MembersStatus
    data object Failed : MembersStatus
}
