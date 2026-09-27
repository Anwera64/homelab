package com.homelab.household.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The member's calendar as the hub keeps it. The password is never sent back. */
@Serializable
data class CalendarCredentialReadDto(
    val id: String,
    val provider: String,
    val url: String,
    val username: String,
    @SerialName("calendar_name") val calendarName: String = "Default",
    @SerialName("updated_at") val updatedAt: String? = null,
    /** Google stopped accepting the sign-in. Hubs from before Google sign-in don't send it. */
    @SerialName("needs_reconnect") val needsReconnect: Boolean = false,
)
