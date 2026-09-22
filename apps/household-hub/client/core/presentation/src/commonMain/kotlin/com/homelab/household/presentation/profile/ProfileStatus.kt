package com.homelab.household.presentation.profile

/** Where the profile stands. */
sealed interface ProfileStatus {
    data object Loading : ProfileStatus

    data object Ready : ProfileStatus

    data object Unreachable : ProfileStatus

    data object Failed : ProfileStatus
}
