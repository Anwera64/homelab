package com.homelab.household.app.screens.changepin

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.homelab.household.presentation.changepin.ChangePinStatus
import com.homelab.household.presentation.changepin.ChangePinUiState
import com.homelab.household.presentation.changepin.CurrentPinError
import com.homelab.household.presentation.firstrun.PinError

/** Changing a PIN, in each state worth drawing. `ChangePinScreenTest` renders them all. */
class ChangePinUiStateProvider : PreviewParameterProvider<ChangePinUiState> {

    private val named = listOf(
        "Empty" to ChangePinUiState(),
        "Filled in" to ChangePinUiState(current = "135790", new = "864209", again = "864209"),
        "Saving it" to ChangePinUiState(
            current = "135790",
            new = "864209",
            again = "864209",
            status = ChangePinStatus.Saving
        ),
        "Wrong current PIN" to ChangePinUiState(new = "864209", again = "864209", currentError = CurrentPinError.Wrong(4)),
        "The two new PINs differ" to ChangePinUiState(current = "135790", new = "864209", again = "864200", againMismatch = true),
        "A PIN that is not six digits" to ChangePinUiState(current = "135790", new = "864", newError = PinError.NotSixDigits),
        "Locked" to ChangePinUiState(new = "864209", again = "864209", status = ChangePinStatus.Locked(secondsLeft = 30)),
        "Hub unreachable" to ChangePinUiState(current = "135790", new = "864209", again = "864209", status = ChangePinStatus.Unreachable)
    )

    override val values: Sequence<ChangePinUiState> = named.map { it.second }.asSequence()

    override fun getDisplayName(index: Int): String = named[index].first
}
