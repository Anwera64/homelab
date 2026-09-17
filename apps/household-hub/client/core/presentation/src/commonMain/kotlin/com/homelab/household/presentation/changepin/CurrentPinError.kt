package com.homelab.household.presentation.changepin

/** What is wrong with the current PIN, shown under that field. */
sealed interface CurrentPinError {
    data object NotSixDigits : CurrentPinError

    /** The hub refused it. It counts toward the same lockout as signing in. */
    data class Wrong(val attemptsLeft: Int) : CurrentPinError
}
