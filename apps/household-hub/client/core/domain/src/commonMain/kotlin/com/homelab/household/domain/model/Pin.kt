package com.homelab.household.domain.model

/** A member's sign-in credential: exactly six digits, nothing else. */
object Pin {
    const val LENGTH = 6

    fun isValid(pin: String): Boolean = pin.length == LENGTH && pin.all { it in '0'..'9' }
}
