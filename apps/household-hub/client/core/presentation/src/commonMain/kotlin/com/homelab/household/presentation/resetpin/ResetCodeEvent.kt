package com.homelab.household.presentation.resetpin

/** What happens once six characters are in: choosing the PIN they unlock. */
sealed interface ResetCodeEvent {
    data class GoToNewPin(val code: String) : ResetCodeEvent
}
