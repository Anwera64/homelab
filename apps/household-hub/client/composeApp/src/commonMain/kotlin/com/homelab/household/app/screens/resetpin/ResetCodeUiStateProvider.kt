package com.homelab.household.app.screens.resetpin

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.homelab.household.presentation.resetpin.ResetCodeUiState

/** The reset-code screen in each state worth drawing. `ResetPinScreenTest` renders them all. */
class ResetCodeUiStateProvider : PreviewParameterProvider<ResetCodeUiState> {
    private val named =
        listOf(
            "Empty" to ResetCodeUiState(),
            "Part typed" to ResetCodeUiState(code = "P4X"),
            "Complete" to ResetCodeUiState(code = "P4XN7T"),
            "Too short" to ResetCodeUiState(code = "P4X", incomplete = true),
        )

    override val values: Sequence<ResetCodeUiState> = named.map { it.second }.asSequence()

    override fun getDisplayName(index: Int): String = named[index].first
}
