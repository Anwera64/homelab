package com.homelab.household.domain.model

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** This phone's offset from UTC at a given moment, in seconds, so dates read in the member's own days. */
@OptIn(ExperimentalTime::class)
fun interface DeviceTimeZone {
    fun offsetSecondsAt(instant: Instant): Int
}
