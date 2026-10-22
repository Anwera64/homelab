package com.homelab.household.data.dto

import kotlinx.serialization.Serializable

/** The keys of the calendars agents may read. Every other calendar of the account is left out. */
@Serializable
data class CalendarSourcesPickDto(
    val picked: List<String>,
)
