package com.homelab.household.presentation.profile

import com.homelab.household.domain.model.User

/** Your own account: who you are, whether the household could manage without you, and your calendar. */
data class ProfileUiState(
    val member: User? = null,
    val isSoleAdmin: Boolean = false,
    val calendar: CalendarRow = CalendarRow.Loading,
    val calendarDisconnect: CalendarDisconnect = CalendarDisconnect.Idle,
    val status: ProfileStatus = ProfileStatus.Loading,
    /** How many writes agents do without asking, for the "When agents act" row. Null makes no claim. */
    val automaticWrites: Int? = null,
    /** "27 yours · 14 household" on the Memories row. Null makes no claim. */
    val memoryCounts: MemoryCounts? = null,
)

data class MemoryCounts(
    val yours: Int,
    val household: Int,
    /** The "2 to check" chip; none at 0. */
    val toCheck: Int = 0,
)
