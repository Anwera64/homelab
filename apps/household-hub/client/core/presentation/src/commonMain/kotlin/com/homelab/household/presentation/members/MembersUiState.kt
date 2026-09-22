package com.homelab.household.presentation.members

/** Everyone who lives on this hub. Inviting and removing are the admin's; vouching is everyone's. */
data class MembersUiState(
    val rows: List<MemberRow> = emptyList(),
    val youAreAdmin: Boolean = false,
    val status: MembersStatus = MembersStatus.Loading,
)
