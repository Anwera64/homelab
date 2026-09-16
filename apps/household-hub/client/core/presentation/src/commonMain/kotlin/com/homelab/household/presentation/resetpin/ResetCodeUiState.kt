package com.homelab.household.presentation.resetpin

/** The code a housemate read out, or the hub printed. */
data class ResetCodeUiState(
    val code: String = "",
    val incomplete: Boolean = false
)
