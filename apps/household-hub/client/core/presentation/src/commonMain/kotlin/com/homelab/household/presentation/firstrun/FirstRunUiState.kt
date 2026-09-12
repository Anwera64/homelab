package com.homelab.household.presentation.firstrun

/** The first-run form: what's typed, what's wrong with it, and how creating the household went. */
data class FirstRunUiState(
    val name: String = "",
    val pin: String = "",
    val colour: String = AvatarPalette.swatches.first(),
    val nameError: NameError? = null,
    val pinError: PinError? = null,
    val isCreating: Boolean = false,
    val failure: FirstRunFailure? = null
)
