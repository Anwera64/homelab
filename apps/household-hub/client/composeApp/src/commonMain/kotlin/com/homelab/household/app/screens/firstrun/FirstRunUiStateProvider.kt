package com.homelab.household.app.screens.firstrun

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.homelab.household.presentation.firstrun.AvatarPalette
import com.homelab.household.presentation.firstrun.FirstRunFailure
import com.homelab.household.presentation.firstrun.FirstRunUiState
import com.homelab.household.presentation.firstrun.NameError
import com.homelab.household.presentation.firstrun.PinError

/** The first-run form in each state worth drawing. `FirstRunScreenTest` renders them all. */
class FirstRunUiStateProvider : PreviewParameterProvider<FirstRunUiState> {
    private val named =
        listOf(
            "Empty" to FirstRunUiState(),
            "Filled in" to FirstRunUiState(name = "Emma Larsson", pin = "482913", colour = AvatarPalette.swatches[1]),
            "Missing fields" to FirstRunUiState(nameError = NameError.Missing, pinError = PinError.NotSixDigits),
            "Hub unreachable" to
                FirstRunUiState(name = "Emma Larsson", pin = "482913", failure = FirstRunFailure.Unreachable),
            "Already set up" to
                FirstRunUiState(name = "Emma Larsson", pin = "482913", failure = FirstRunFailure.AlreadySetUp),
        )

    override val values: Sequence<FirstRunUiState> = named.map { it.second }.asSequence()

    override fun getDisplayName(index: Int): String = named[index].first
}
