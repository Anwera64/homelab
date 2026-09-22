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
 * Home is still the placeholder slice 3 replaces — but the profile is reached from the avatar on a
 * dashboard, so the avatar is here already.
 */
@Composable
fun HomePlaceholderScreen(
    onProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ProfileViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val member = state.member
    val description = stringResource(Res.string.profile_open)

    HomePlaceholderContent(modifier, member, onProfile, description)
}
