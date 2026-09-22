package com.homelab.household.app.screens.join

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.homelab.household.domain.model.InvitePreview
import com.homelab.household.presentation.firstrun.AvatarPalette
import com.homelab.household.presentation.firstrun.NameError
import com.homelab.household.presentation.firstrun.PinError
import com.homelab.household.presentation.join.JoinStatus
import com.homelab.household.presentation.join.JoinUiState

/** The join form in each state worth drawing. `JoinScreenTest` renders them all. */
class JoinUiStateProvider : PreviewParameterProvider<JoinUiState> {
    private val invite = InvitePreview(invitedName = "Liam", inviterName = "Emma", inviterAvatarColor = "#3C6E4E")
    private val base = JoinUiState(preview = invite, colour = AvatarPalette.swatches.first())

    private val named =
        listOf(
            "From the invite" to base,
            "Filled in" to base.copy(pin = "975310", colour = AvatarPalette.swatches[1]),
            "Joining" to base.copy(pin = "975310", status = JoinStatus.Joining),
            "A colour somebody wears" to
                base.copy(
                    takenColours = setOf(AvatarPalette.swatches.first()),
                    colour = AvatarPalette.swatches[1],
                ),
            "Missing fields" to base.copy(nameError = NameError.Missing, pinError = PinError.NotSixDigits),
            "Name taken" to base.copy(pin = "975310", nameError = NameError.Taken),
            "Code expired" to base.copy(pin = "975310", status = JoinStatus.Expired),
            "Hub unreachable" to base.copy(pin = "975310", status = JoinStatus.Unreachable),
        )

    override val values: Sequence<JoinUiState> = named.map { it.second }.asSequence()

    override fun getDisplayName(index: Int): String = named[index].first
}
