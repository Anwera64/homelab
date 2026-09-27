package com.homelab.household.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Connecting a calendar. A null [calendarName] is left out, and the hub uses the account's first calendar. */
@Serializable
data class CalendarCredentialCreateDto(
    val provider: String,
    val url: String,
    val username: String,
    val password: String,
    @SerialName("calendar_name") val calendarName: String? = null,
)
