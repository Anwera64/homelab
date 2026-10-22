package com.homelab.household.data.dto

import kotlinx.serialization.Serializable

/** Connecting a calendar. The hub names it by default, and the member picks calendars after connecting. */
@Serializable
data class CalendarCredentialCreateDto(
    val provider: String,
    val url: String,
    val username: String,
    val password: String,
)
