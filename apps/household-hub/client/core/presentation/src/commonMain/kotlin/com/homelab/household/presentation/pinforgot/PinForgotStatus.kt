package com.homelab.household.presentation.pinforgot

/** Where the forgotten-PIN screen stands. */
sealed interface PinForgotStatus {
    data object Loading : PinForgotStatus
    data object Ready : PinForgotStatus
    data object Unreachable : PinForgotStatus
    data object Failed : PinForgotStatus
}
