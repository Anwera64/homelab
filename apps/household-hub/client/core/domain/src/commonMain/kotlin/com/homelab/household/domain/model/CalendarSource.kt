package com.homelab.household.domain.model

/** One calendar of the member's account, and whether agents read it. */
data class CalendarSource(
    val key: String,
    val name: String,
    val writable: Boolean,
    val picked: Boolean,
)
