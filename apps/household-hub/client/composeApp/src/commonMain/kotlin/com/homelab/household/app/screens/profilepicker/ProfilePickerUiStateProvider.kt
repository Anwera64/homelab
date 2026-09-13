package com.homelab.household.app.screens.profilepicker

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.homelab.household.domain.model.Member
import com.homelab.household.presentation.profilepicker.PickerStatus
import com.homelab.household.presentation.profilepicker.ProfilePickerUiState

/** "Who's here?" in every state it can be in. `ProfilePickerScreenTest` renders them all. */
class ProfilePickerUiStateProvider : PreviewParameterProvider<ProfilePickerUiState> {

    private val named = listOf(
        "Loading" to PickerStatus.Loading,
        "Two members" to PickerStatus.Loaded(
            listOf(
                Member(id = "emma", name = "Emma", avatarColor = "#3C6E4E"),
                Member(id = "liam", name = "Liam", avatarColor = "#C05638")
            )
        ),
        "Unreachable" to PickerStatus.Unreachable,
        "Failed" to PickerStatus.Failed
    )

    override val values: Sequence<ProfilePickerUiState> = named.map { ProfilePickerUiState(status = it.second) }.asSequence()

    override fun getDisplayName(index: Int): String = named[index].first
}
