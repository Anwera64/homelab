package com.homelab.household.app.screens.placeholder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.household.app.components.HearthScaffold
import com.homelab.household.app.components.MemberAvatar
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.home_detail
import com.homelab.household.app.resources.home_title
import com.homelab.household.app.resources.profile_open
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.presentation.profile.ProfileViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** The default colour a member wears when the hub hasn't said. */
private const val DEFAULT_COLOUR = "#3C6E4E"

/**
 * Home is still the placeholder slice 3 replaces — but the profile is reached from the avatar on a
 * dashboard, so the avatar is here already.
 */
@Composable
fun HomePlaceholderScreen(
    onProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: ProfileViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val member = state.member
    val description = stringResource(Res.string.profile_open)

    HearthScaffold(
        modifier = modifier,
        header = {
            Box(
                modifier = Modifier.fillMaxWidth().padding(HearthTheme.spacing.lg),
                contentAlignment = Alignment.CenterEnd
            ) {
                MemberAvatar(
                    name = member?.fullName.orEmpty(),
                    colour = member?.avatarColor ?: DEFAULT_COLOUR,
                    size = HearthTheme.size.touchTarget,
                    glyph = HearthTheme.typography.glyphMd,
                    modifier = Modifier
                        .clickable(onClick = onProfile)
                        .semantics { contentDescription = description }
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center) {
            PlaceholderContent(
                title = stringResource(Res.string.home_title),
                detail = stringResource(Res.string.home_detail)
            )
        }
    }
}
