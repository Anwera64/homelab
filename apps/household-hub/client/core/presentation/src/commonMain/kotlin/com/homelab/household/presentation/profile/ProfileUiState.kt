package com.homelab.household.presentation.profile

import com.homelab.household.domain.model.User

/** Your own account: who you are, whether the household could manage without you, and your calendar. */
data class ProfileUiState(
    val member: User? = null,
    val isSoleAdmin: Boolean = false,
    val calendar: CalendarRow = CalendarRow.Loading,
    val calendarDisconnect: CalendarDisconnect = CalendarDisconnect.Idle,
    val status: ProfileStatus = ProfileStatus.Loading,
)
