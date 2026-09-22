package com.homelab.household.presentation.changepin

import com.homelab.household.presentation.firstrun.PinError

/** Changing your own PIN: the one you have, the one you want, and it typed once more. */
data class ChangePinUiState(
    val current: String = "",
    val new: String = "",
    val again: String = "",
    val currentError: CurrentPinError? = null,
    val newError: PinError? = null,
    val againMismatch: Boolean = false,
    val status: ChangePinStatus = ChangePinStatus.Idle,
)
