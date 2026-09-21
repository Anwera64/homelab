package com.homelab.household.app.screens.profile

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.homelab.household.domain.model.User
import com.homelab.household.presentation.profile.ProfileStatus
import com.homelab.household.presentation.profile.ProfileUiState

/** The profile in each state worth drawing. `ProfileScreenTest` renders them all. */
class ProfileUiStateProvider : PreviewParameterProvider<ProfileUiState> {

    private val emma = User(
        id = "emma",
        fullName = "Emma Larsson",
        isAdmin = true,
        isActive = true,
        avatarColor = "#3C6E4E"
    )
    private val liam = emma.copy(id = "liam", fullName = "Liam", isAdmin = false, avatarColor = "#C05638")

    private val named = listOf(
        "The only admin" to ProfileUiState(member = emma, isSoleAdmin = true, status = ProfileStatus.Ready),
        "An admin who can leave" to ProfileUiState(member = emma, isSoleAdmin = false, status = ProfileStatus.Ready),
        "A member" to ProfileUiState(member = liam, isSoleAdmin = false, status = ProfileStatus.Ready),
        // Arriving knows nothing yet — not the name, not whether leaving is even possible.
        "Arriving" to ProfileUiState(member = null, status = ProfileStatus.Loading),
        "Hub unreachable" to ProfileUiState(member = emma, status = ProfileStatus.Unreachable)
    )

    override val values: Sequence<ProfileUiState> = named.map { it.second }.asSequence()

    override fun getDisplayName(index: Int): String = named[index].first
}
