package com.homelab.household.app.screens.members

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.homelab.household.app.components.BentoCard
import com.homelab.household.app.components.ChipVariant
import com.homelab.household.app.components.DestructiveButton
import com.homelab.household.app.components.HearthChip
import com.homelab.household.app.components.HearthProgressBar
import com.homelab.household.app.components.HearthProgressBarWidth
import com.homelab.household.app.components.HearthScaffold
import com.homelab.household.app.components.HearthTopBar
import com.homelab.household.app.components.MemberAvatar
import com.homelab.household.app.components.PrimaryButton
import com.homelab.household.app.components.SecondaryButton
import com.homelab.household.app.components.SkeletonBlock
import com.homelab.household.app.components.SkeletonCircle
import com.homelab.household.app.components.SkeletonGroup
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.members_admin
import com.homelab.household.app.resources.members_back
import com.homelab.household.app.resources.members_detail
import com.homelab.household.app.resources.members_failed
import com.homelab.household.app.resources.members_invite
import com.homelab.household.app.resources.members_member
import com.homelab.household.app.resources.members_remove
import com.homelab.household.app.resources.members_reset_pin
import com.homelab.household.app.resources.members_retry
import com.homelab.household.app.resources.members_title
import com.homelab.household.app.resources.members_unreachable
import com.homelab.household.app.resources.members_you
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.app.theme.PreviewDayNight
import com.homelab.household.presentation.members.MemberRow
import com.homelab.household.presentation.members.MembersStatus
import com.homelab.household.presentation.members.MembersUiState
import org.jetbrains.compose.resources.stringResource

/**
 * Everyone who lives on this hub. Inviting and removing belong to the admin; vouching for a
 * forgotten PIN belongs to everybody, which is why Reset PIN is on every other member's row.
 *
 * Stateless — [MembersScreen] owns the ViewModel.
 */
@Composable
fun MembersContent(
    state: MembersUiState,
    onInvite: () -> Unit,
    onResetPin: (MemberRow) -> Unit,
    onRemove: (MemberRow) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography

    // Two shapes of wait, and the rows are what tells them apart (design notes §6.21). Arriving
    // has nothing to show yet, so blocks stand where the members will be; coming back already has
    // them, and replacing readable rows with blocks would be a step backwards.
    val loading = state.status == MembersStatus.Loading
    val arriving = loading && state.rows.isEmpty()

    HearthScaffold(
        modifier = modifier,
        header = {
            Column {
                HearthTopBar(onBack = onBack, backDescription = stringResource(Res.string.members_back))
                if (loading && !arriving) {
                    HearthProgressBar(width = HearthProgressBarWidth.FullBleed)
                }
            }
        },
        bottomBar = {
            // Not while arriving: whether this phone is the admin's is one of the things the hub
            // has not said yet, and a button that may not be yours is worse than none.
            if (state.youAreAdmin && !arriving) {
                Column(modifier = Modifier.fillMaxWidth().padding(HearthTheme.spacing.xl)) {
                    PrimaryButton(
                        text = stringResource(Res.string.members_invite),
                        onClick = onInvite,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md)) {
                    Text(stringResource(Res.string.members_title), style = type.hero, color = colors.textPrimary)
                    Text(stringResource(Res.string.members_detail), style = type.body, color = colors.textMuted)
                }
            }

            if (arriving) {
                item {
                    SkeletonGroup {
                        repeat(ARRIVING_CARDS) { position -> MemberCardSkeleton(position) }
                    }
                }
            }

            items(state.rows, key = { it.id }) { row ->
                MemberCard(
                    row = row,
                    youAreAdmin = state.youAreAdmin,
                    onResetPin = { onResetPin(row) },
                    onRemove = { onRemove(row) },
                )
            }

            failureItem(state.status, onRetry)
        }
    }
}

/**
 * A member card that has not arrived. Drawn at the real card's geometry — the same avatar size,
 * the same divider, the same two button-height rows — so nothing moves when the answer lands.
 *
 * [position] sets its breath a beat behind the card above it, so two of them read as one list
 * arriving rather than two lights blinking together.
 */
@Composable
private fun MemberCardSkeleton(position: Int) {
    val size = HearthTheme.size

    BentoCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkeletonCircle(size = size.touchTarget, position = position)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm),
            ) {
                SkeletonBlock(widthFraction = NAME_WIDTH, position = position)
                SkeletonBlock(widthFraction = ROLE_WIDTH, height = HearthTheme.spacing.md, position = position)
            }
        }

        HorizontalDivider(color = HearthTheme.colors.outlineSoft)
        Row(horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md)) {
            repeat(2) {
                SkeletonBlock(
                    modifier = Modifier.weight(1f),
                    height = size.control,
                    position = position,
                )
            }
        }
    }
}

@Composable
private fun MemberCard(
    row: MemberRow,
    youAreAdmin: Boolean,
    onResetPin: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography

    BentoCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MemberAvatar(
                name = row.name,
                colour = row.avatarColor,
                size = HearthTheme.size.touchTarget,
                glyph = type.glyphMd,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xxs),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(row.name, style = type.bodyStrong, color = colors.textPrimary)
                    if (row.isYou) {
                        HearthChip(label = stringResource(Res.string.members_you), variant = ChipVariant.Neutral)
                    }
                }
                if (row.isAdmin) {
                    HearthChip(label = stringResource(Res.string.members_admin), variant = ChipVariant.Primary)
                } else {
                    Text(stringResource(Res.string.members_member), style = type.caption, color = colors.textMuted)
                }
            }
        }

        if (!row.isYou) {
            HorizontalDivider(color = colors.outlineSoft)
            Row(horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md)) {
                SecondaryButton(
                    text = stringResource(Res.string.members_reset_pin),
                    onClick = onResetPin,
                    modifier = Modifier.weight(1f),
                )
                if (youAreAdmin) {
                    DestructiveButton(
                        text = stringResource(Res.string.members_remove),
                        onClick = onRemove,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** How many stand-ins a first load draws: a household of two is the case this app was built for. */
private const val ARRIVING_CARDS = 2

/** How much of the row a skeleton line fills. Unequal, so it reads as a wait and not as a grid. */
private const val NAME_WIDTH = 0.45f
private const val ROLE_WIDTH = 0.25f

private fun androidx.compose.foundation.lazy.LazyListScope.failureItem(
    status: MembersStatus,
    onRetry: () -> Unit,
) {
    when (status) {
        MembersStatus.Loading, MembersStatus.Ready -> {
            Unit
        }

        else -> {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = HearthTheme.spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md),
                ) {
                    val text =
                        if (status is MembersStatus.Unreachable) {
                            stringResource(Res.string.members_unreachable)
                        } else {
                            stringResource(Res.string.members_failed)
                        }
                    Text(text, style = HearthTheme.typography.body, color = HearthTheme.colors.error)
                    SecondaryButton(text = stringResource(Res.string.members_retry), onClick = onRetry)
                }
            }
        }
    }
}

@PreviewDayNight
@Composable
private fun MembersContentPreview(
    @PreviewParameter(MembersUiStateProvider::class) state: MembersUiState,
) {
    HearthTheme {
        MembersContent(state = state, onInvite = {}, onResetPin = {}, onRemove = {}, onRetry = {}, onBack = {})
    }
}
