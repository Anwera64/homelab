package com.homelab.household.data.dto

import kotlinx.serialization.Serializable

/** One calendar of the member's account as the hub lists it, and whether agents read it. */
@Serializable
data class CalendarSourceDto(
    val key: String,
    val name: String,
    val writable: Boolean,
    val picked: Boolean,
)
