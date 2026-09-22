package com.homelab.household.app.screens.resetpin

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.homelab.household.presentation.firstrun.PinError
import com.homelab.household.presentation.resetpin.NewPinStatus
import com.homelab.household.presentation.resetpin.NewPinUiState

/** The new-PIN screen in each state worth drawing. `ResetPinScreenTest` renders them all. */
class NewPinUiStateProvider : PreviewParameterProvider<NewPinUiState> {
    private val named =
        listOf(
            "Empty" to NewPinUiState(),
            "Filled in" to NewPinUiState(pin = "864209", again = "864209"),
            "Setting it" to NewPinUiState(pin = "864209", again = "864209", status = NewPinStatus.Setting),
            "The two differ" to NewPinUiState(pin = "864209", again = "864200", againMismatch = true),
            "Not six digits" to NewPinUiState(pin = "864", pinError = PinError.NotSixDigits),
            "Code has gone" to NewPinUiState(pin = "864209", again = "864209", status = NewPinStatus.Invalid),
            "Guessing locked" to
                NewPinUiState(pin = "864209", again = "864209", status = NewPinStatus.Locked(secondsLeft = 30)),
            "Hub unreachable" to NewPinUiState(pin = "864209", again = "864209", status = NewPinStatus.Unreachable),
        )

    override val values: Sequence<NewPinUiState> = named.map { it.second }.asSequence()

    override fun getDisplayName(index: Int): String = named[index].first
}
