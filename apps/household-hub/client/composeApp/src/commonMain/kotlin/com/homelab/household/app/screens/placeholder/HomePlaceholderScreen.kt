package com.homelab.household.app.screens.placeholder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.profile_open
import com.homelab.household.presentation.profile.ProfileViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The dashboards slice 3 does not build: Household, Schedule and My Space.
 *
 * Everything drawn on them — the morning briefing, both calendars, Notes — belongs to slices 8, 9
 * and 11, and none of it has an endpoint yet. They are reachable so the daily loop can be walked
 * end to end, and they carry the tab bar and the avatar, which do work.
 */
@Composable
fun HomePlaceholderScreen(
    onProfile: () -> Unit,
    tabs: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ProfileViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val member = state.member
    val description = stringResource(Res.string.profile_open)

    HomePlaceholderContent(member, onProfile, description, tabs, modifier)
}
