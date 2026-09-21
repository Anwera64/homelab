package com.homelab.household.app.screens.invitecreate

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.homelab.household.domain.model.Invite
import com.homelab.household.presentation.firstrun.NameError
import com.homelab.household.presentation.invitecreate.InviteCreateStatus
import com.homelab.household.presentation.invitecreate.InviteCreateUiState

/** Inviting someone, in each state worth drawing. `InviteCreateScreenTest` renders them all. */
class InviteCreateUiStateProvider : PreviewParameterProvider<InviteCreateUiState> {

    private val invite = Invite(code = "K7M2QP", invitedName = "Liam", isAdmin = false, expiresInSeconds = 892)

    private val named = listOf(
        "Empty" to InviteCreateUiState(),
        "A name, no code yet" to InviteCreateUiState(name = "Liam"),
        "Joining as an admin" to InviteCreateUiState(name = "Noor", isAdmin = true),
        "Making the first code" to InviteCreateUiState(name = "Liam", status = InviteCreateStatus.Creating),
        "Making a second code" to InviteCreateUiState(
            name = "Liam",
            invite = invite,
            secondsLeft = 892,
            status = InviteCreateStatus.Creating
        ),
        "Code to read out" to InviteCreateUiState(name = "Liam", invite = invite, secondsLeft = 892),
        "Code expired" to InviteCreateUiState(
            name = "Liam",
            invite = invite,
            secondsLeft = 0,
            status = InviteCreateStatus.Expired
        ),
        "No name given" to InviteCreateUiState(nameError = NameError.Missing),
        "Name taken" to InviteCreateUiState(name = "Emma", nameError = NameError.Taken),
        "Hub unreachable" to InviteCreateUiState(name = "Liam", status = InviteCreateStatus.Unreachable)
    )

    override val values: Sequence<InviteCreateUiState> = named.map { it.second }.asSequence()

    override fun getDisplayName(index: Int): String = named[index].first
}
