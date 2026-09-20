package com.homelab.household.app.util

/** Seconds as a countdown reads on a screen: 14:52. */
fun Int.asCountdown(): String {
    val minutes = this / 60
    val seconds = this % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
