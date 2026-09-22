package com.homelab.household.app.screens.members

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.homelab.household.presentation.members.MemberRow
import com.homelab.household.presentation.members.MembersStatus
import com.homelab.household.presentation.members.MembersUiState

/** The members list in each state worth drawing. `MembersScreenTest` renders them all. */
class MembersUiStateProvider : PreviewParameterProvider<MembersUiState> {
    private val emma =
        MemberRow(id = "emma", name = "Emma Larsson", avatarColor = "#3C6E4E", isYou = true, isAdmin = true)
    private val liam = MemberRow(id = "liam", name = "Liam", avatarColor = "#C05638", isYou = false, isAdmin = false)

    private val named =
        listOf(
            "Seen by the admin" to
                MembersUiState(rows = listOf(emma, liam), youAreAdmin = true, status = MembersStatus.Ready),
            "Seen by a member" to
                MembersUiState(
                    rows = listOf(liam.copy(isYou = true), emma.copy(isYou = false)),
                    youAreAdmin = false,
                    status = MembersStatus.Ready,
                ),
            // Arriving has no rows and no bottom button: whether this phone is the admin's is one of
            // the things the hub has not said yet.
            "Arriving" to MembersUiState(status = MembersStatus.Loading),
            "Coming back" to
                MembersUiState(
                    rows = listOf(emma, liam),
                    youAreAdmin = true,
                    status = MembersStatus.Loading,
                ),
            "Hub unreachable" to MembersUiState(status = MembersStatus.Unreachable),
            "Hub answered badly" to MembersUiState(status = MembersStatus.Failed),
        )

    override val values: Sequence<MembersUiState> = named.map { it.second }.asSequence()

    override fun getDisplayName(index: Int): String = named[index].first
}
