package com.homelab.household.domain.model

/** A member's name, which is how the profile picker tells people apart. */
object MemberName {
    const val MAX_LENGTH = 128

    fun isValid(name: String): Boolean = name.trim().length in 1..MAX_LENGTH
}
