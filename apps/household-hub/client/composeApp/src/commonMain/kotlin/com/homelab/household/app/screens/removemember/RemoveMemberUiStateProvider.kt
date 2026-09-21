package com.homelab.household.app.screens.removemember

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.homelab.household.domain.model.Member
import com.homelab.household.presentation.removemember.RemoveMemberStatus
import com.homelab.household.presentation.removemember.RemoveMemberUiState

/** Removing a member, in each state worth drawing. `RemoveMemberScreenTest` renders them all. */
class RemoveMemberUiStateProvider : PreviewParameterProvider<RemoveMemberUiState> {

    private val liam = Member(id = "liam", name = "Liam", avatarColor = "#C05638")
    private val base = RemoveMemberUiState(member = liam)

    private val named = listOf(
        "Nothing typed" to base,
        "Half typed" to base.copy(typedName = "Li"),
        "Name typed out" to base.copy(typedName = "Liam"),
        "Removing them" to base.copy(typedName = "Liam", status = RemoveMemberStatus.Removing),
        "Another name" to base.copy(typedName = "Emma", nameMismatch = true),
        "Hub unreachable" to base.copy(typedName = "Liam", status = RemoveMemberStatus.Unreachable)
    )

    override val values: Sequence<RemoveMemberUiState> = named.map { it.second }.asSequence()

    override fun getDisplayName(index: Int): String = named[index].first
}
