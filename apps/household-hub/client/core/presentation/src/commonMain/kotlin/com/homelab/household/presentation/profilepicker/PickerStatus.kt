package com.homelab.household.presentation.profilepicker

import com.homelab.household.domain.model.Member

/** What the profile picker found when it asked the hub who lives here. */
sealed interface PickerStatus {
    data object Loading : PickerStatus
    data class Loaded(val members: List<Member>) : PickerStatus
    data object Unreachable : PickerStatus
    data object Failed : PickerStatus
}
