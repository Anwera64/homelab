package com.homelab.household.presentation.profilepicker

import com.homelab.household.domain.model.Member

/** Where the picker sends the user: to the PIN pad of the face they tapped. */
sealed interface ProfilePickerEvent {
    data class GoToPin(val member: Member) : ProfilePickerEvent
}
