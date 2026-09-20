package com.homelab.household.presentation.resetpin

import com.homelab.household.presentation.firstrun.PinError

/** Choosing the PIN a reset code sets, typed twice so a slip cannot lock anyone out again. */
data class NewPinUiState(
    val pin: String = "",
    val again: String = "",
    val pinError: PinError? = null,
    val againMismatch: Boolean = false,
    val status: NewPinStatus = NewPinStatus.Idle
)
