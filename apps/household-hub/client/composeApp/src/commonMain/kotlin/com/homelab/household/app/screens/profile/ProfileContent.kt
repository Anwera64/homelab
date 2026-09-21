package com.homelab.household.app.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.homelab.household.app.components.BentoCard
import com.homelab.household.app.components.DestructiveButton
import com.homelab.household.app.components.HearthChip
import com.homelab.household.app.components.ChipVariant
import com.homelab.household.app.components.HearthScaffold
import com.homelab.household.app.components.HearthTopBar
import com.homelab.household.app.components.MemberAvatar
import com.homelab.household.app.components.SettingsRow
import com.homelab.household.app.components.SkeletonBlock
import com.homelab.household.app.components.SkeletonCircle
import com.homelab.household.app.components.SkeletonGroup
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.members_failed
import com.homelab.household.app.resources.members_unreachable
import com.homelab.household.app.resources.profile_admin
import com.homelab.household.app.resources.profile_back
import com.homelab.household.app.resources.profile_change_pin
import com.homelab.household.app.resources.profile_delete_account
import com.homelab.household.app.resources.profile_delete_blocked
import com.homelab.household.app.resources.profile_members
import com.homelab.household.app.resources.profile_sign_out
import com.homelab.household.app.theme.DayNightPreviews
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.presentation.profile.ProfileStatus
import com.homelab.household.presentation.profile.ProfileUiState
import org.jetbrains.compose.resources.stringResource

/** The colour a member wears when the hub hasn't said which. */
private const val DEFAULT_COLOUR = "#3C6E4E"

/**
 * Your own account. Everything a later slice adds — memory, agents, calendar, appearance — lands
 * here; for now it holds what slice 2 backs, and states the one limit the admin cannot work around.
 *
 * Stateless — [ProfileScreen] owns the ViewModel.
 */
@Composable
fun ProfileContent(
    state: ProfileUiState,
    onBack: () -> Unit,
    onMembers: () -> Unit,
    onChangePin: () -> Unit,
    onLeave: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography
    val member = state.member

    HearthScaffold(
        modifier = modifier,
        header = { HearthTopBar(onBack = onBack, backDescription = stringResource(Res.string.profile_back)) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding),
            verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xxl)
        ) {
            // Only the header depends on the hub. Everything below it is this screen's own copy,
            // so it draws from the first frame rather than waiting to be told (§6.21, pattern 2).
            if (member == null) {
                ArrivingHeader()
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.lg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MemberAvatar(
                        name = member.fullName,
                        colour = member.avatarColor ?: DEFAULT_COLOUR,
                        size = HearthTheme.size.tile,
                        glyph = type.glyphXxl
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm)) {
                        Text(member.fullName, style = type.title, color = colors.textPrimary)
                        if (member.isAdmin) {
                            HearthChip(label = stringResource(Res.string.profile_admin), variant = ChipVariant.Primary)
                        }
                    }
                }
            }

            BentoCard(modifier = Modifier.fillMaxWidth()) {
                SettingsRow(
                    label = stringResource(Res.string.profile_members),
                    icon = HearthIcon.Shared,
                    onClick = onMembers
                )
                HorizontalDivider(color = colors.outlineSoft)
                SettingsRow(
                    label = stringResource(Res.string.profile_change_pin),
                    icon = HearthIcon.SecretLocked,
                    onClick = onChangePin
                )
                HorizontalDivider(color = colors.outlineSoft)
                // Dimming is for what cannot be opened, and the caption says why (design notes §2).
                SettingsRow(
                    label = stringResource(Res.string.profile_delete_account),
                    icon = HearthIcon.Delete,
                    caption = if (state.isSoleAdmin) stringResource(Res.string.profile_delete_blocked) else null,
                    enabled = !state.isSoleAdmin,
                    onClick = onLeave
                )
            }

            failure(state.status)?.let { failure ->
                Text(failure, style = type.caption, color = colors.error)
            }

            DestructiveButton(
                text = stringResource(Res.string.profile_sign_out),
                onClick = onSignOut,
                icon = HearthIcon.SignOut,
                modifier = Modifier.fillMaxWidth().padding(bottom = HearthTheme.spacing.xxl)
            )
        }
    }
}

/**
 * Your name and whether you are the admin, before the hub has said either. Drawn at the real
 * header's geometry — the same avatar circle, a line for the name and a shorter one for the chip —
 * so the header does not jump when the answer lands.
 */
@Composable
private fun ArrivingHeader() {
    SkeletonGroup {
        Row(
            horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SkeletonCircle(size = HearthTheme.size.tile)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm)
            ) {
                SkeletonBlock(widthFraction = NAME_WIDTH, height = HearthTheme.spacing.xl)
                SkeletonBlock(widthFraction = CHIP_WIDTH, height = HearthTheme.spacing.lg)
            }
        }
    }
}

/** How much of the row the name and the admin chip fill before either has arrived. */
private const val NAME_WIDTH = 0.6f
private const val CHIP_WIDTH = 0.3f

@Composable
private fun failure(status: ProfileStatus): String? = when (status) {
    ProfileStatus.Loading, ProfileStatus.Ready -> null
    ProfileStatus.Unreachable -> stringResource(Res.string.members_unreachable)
    ProfileStatus.Failed -> stringResource(Res.string.members_failed)
}

@DayNightPreviews
@Composable
private fun ProfileContentPreview(
    @PreviewParameter(ProfileUiStateProvider::class) state: ProfileUiState
) {
    HearthTheme {
        ProfileContent(
            state = state,
            onBack = {},
            onMembers = {},
            onChangePin = {},
            onLeave = {},
            onSignOut = {}
        )
    }
}
