package com.homelab.household.app.screens.launch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.homelab.household.app.components.BentoCard
import com.homelab.household.app.components.ChipVariant
import com.homelab.household.app.components.HearthChip
import com.homelab.household.app.components.PrimaryButton
import com.homelab.household.app.components.SecondaryButton
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.icons.HearthIconImage
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.app_name
import com.homelab.household.app.resources.launch_action_description
import com.homelab.household.app.resources.launch_checking
import com.homelab.household.app.resources.launch_failed_address_not_found
import com.homelab.household.app.resources.launch_failed_not_json
import com.homelab.household.app.resources.launch_failed_title
import com.homelab.household.app.resources.launch_failed_unknown
import com.homelab.household.app.resources.launch_failed_upstream
import com.homelab.household.app.resources.launch_first_run_detail
import com.homelab.household.app.resources.launch_first_run_title
import com.homelab.household.app.resources.launch_member_count
import com.homelab.household.app.resources.launch_no_route
import com.homelab.household.app.resources.launch_offline_hint
import com.homelab.household.app.resources.launch_open_tailscale
import com.homelab.household.app.resources.launch_ready
import com.homelab.household.app.resources.launch_retry
import com.homelab.household.app.resources.launch_retrying_in
import com.homelab.household.app.resources.launch_retrying_seconds
import com.homelab.household.app.resources.launch_unreachable_detail
import com.homelab.household.app.resources.launch_unreachable_title
import com.homelab.household.app.theme.DayNightPreviews
import com.homelab.household.app.theme.HearthShapes
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.presentation.launch.HubFailure
import com.homelab.household.presentation.launch.HubStatus
import com.homelab.household.presentation.launch.LaunchUiState
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The first screen: what `GET /auth/status` found. Fixed shape, so it doesn't scroll.
 *
 * Stateless — [LaunchScreen] owns the ViewModel and hands the state down.
 */
@Composable
fun LaunchContent(
    state: LaunchUiState,
    onRetry: () -> Unit,
    onOpenTailscale: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.status is HubStatus.Unreachable) {
        OfflineContent(state = state, onRetry = onRetry, onOpenTailscale = onOpenTailscale, modifier = modifier)
    } else {
        AnsweredContent(state = state, onRetry = onRetry, modifier = modifier)
    }
}

/** The hub answered — or is being asked. */
@Composable
private fun AnsweredContent(state: LaunchUiState, onRetry: () -> Unit, modifier: Modifier) {
    val colors = HearthTheme.colors
    val status = state.status
    val failed = status is HubStatus.Failed

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
            .padding(HearthTheme.spacing.huge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            HearthTheme.spacing.xxxl,
            Alignment.CenterVertically
        )
    ) {
        if (failed) {
            OfflineTile()
        } else {
            Box(
                modifier = Modifier
                    .size(HearthTheme.size.tileHero)
                    .background(
                        color = colors.primary,
                        shape = HearthShapes.tile
                    ),
                contentAlignment = Alignment.Center
            ) {
                HearthIconImage(
                    icon = HearthIcon.Household,
                    contentDescription = null,
                    size = HearthTheme.size.iconHero,
                    tint = colors.onPrimary
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm)
        ) {
            Text(
                text = stringResource(
                    if (failed) Res.string.launch_unreachable_title else Res.string.app_name
                ),
                // A hub that answered gets the full-screen title; one that failed gets the
                // smaller one, because the reason underneath is the part you need to read.
                style = with(HearthTheme.typography) { if (failed) title else hero },
                color = colors.textPrimary,
                textAlign = TextAlign.Center
            )
            StatusLines(
                status = status,
                modifier = Modifier.widthIn(max = HearthTheme.size.readingWidth)
            )
        }

        HubAddressPill(
            hubAddress = state.hubAddress,
            reachable = !failed
        )

        if (status is HubStatus.Checking) {
            LinearProgressIndicator(
                modifier = Modifier
                    .width(HearthTheme.size.progressTrack)
                    .height(HearthTheme.size.progressHeight),
                color = colors.primary,
                trackColor = colors.outline,
                strokeCap = StrokeCap.Round,
                gapSize = HearthTheme.spacing.none
            )
            Text(
                text = stringResource(Res.string.launch_action_description),
                style = HearthTheme.typography.monoSm,
                color = colors.textMuted
            )
        }

        if (failed) {
            PrimaryButton(
                text = stringResource(Res.string.launch_retry),
                onClick = onRetry,
                icon = HearthIcon.Retry,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Nothing answered. Not an error page: a hub that's off and a phone away from home without
 * Tailscale both land here before anything is typed, so it says which ways back there are.
 */
@Composable
private fun OfflineContent(
    state: LaunchUiState,
    onRetry: () -> Unit,
    onOpenTailscale: () -> Unit,
    modifier: Modifier
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
            .padding(horizontal = HearthTheme.spacing.xxxl, vertical = HearthTheme.spacing.huge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xxl, Alignment.CenterVertically)
    ) {
        OfflineTile()

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md)
        ) {
            Text(
                text = stringResource(Res.string.launch_unreachable_title),
                style = type.title,
                color = colors.textPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(Res.string.launch_unreachable_detail),
                style = type.body,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = HearthTheme.size.readingWidth)
            )
        }

        BentoCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md)
            ) {
                Text(
                    text = state.hubAddress,
                    style = type.monoSm,
                    color = colors.textMuted,
                    modifier = Modifier.weight(1f)
                )
                HearthChip(label = stringResource(Res.string.launch_no_route), variant = ChipVariant.Error)
            }
            state.retryInSeconds?.let { seconds ->
                HorizontalDivider(thickness = HearthTheme.size.hairline, color = colors.outlineSoft)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md)
                ) {
                    Text(
                        text = stringResource(Res.string.launch_retrying_in),
                        style = type.monoSm,
                        color = colors.textMuted,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = stringResource(Res.string.launch_retrying_seconds, seconds),
                        style = type.monoSm,
                        color = colors.textPrimary
                    )
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md)
        ) {
            PrimaryButton(
                text = stringResource(Res.string.launch_retry),
                onClick = onRetry,
                icon = HearthIcon.Retry,
                modifier = Modifier.fillMaxWidth()
            )
            SecondaryButton(
                text = stringResource(Res.string.launch_open_tailscale),
                onClick = onOpenTailscale,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Text(
            text = stringResource(Res.string.launch_offline_hint),
            style = type.caption,
            color = colors.textMuted,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun OfflineTile() {
    val colors = HearthTheme.colors
    Box(
        modifier = Modifier
            .size(HearthTheme.size.tile)
            .background(
                color = colors.errorContainer,
                shape = HearthShapes.tile
            ),
        contentAlignment = Alignment.Center
    ) {
        HearthIconImage(
            icon = HearthIcon.HubOffline,
            contentDescription = null,
            size = HearthTheme.size.iconXxl,
            tint = colors.onErrorContainer
        )
    }
}

@Composable
private fun StatusLines(status: HubStatus, modifier: Modifier = Modifier) {
    val colors = HearthTheme.colors
    val lines = when (status) {
        HubStatus.Checking -> listOf(stringResource(Res.string.launch_checking))
        is HubStatus.Ready -> listOf(
            stringResource(Res.string.launch_ready),
            pluralStringResource(
                Res.plurals.launch_member_count,
                status.memberCount,
                status.memberCount
            )
        )

        HubStatus.FirstRun -> listOf(
            stringResource(Res.string.launch_first_run_title),
            stringResource(Res.string.launch_first_run_detail)
        )

        HubStatus.Unreachable -> listOf(stringResource(Res.string.launch_unreachable_detail))
        is HubStatus.Failed -> listOf(
            stringResource(Res.string.launch_failed_title),
            whatWentWrong(status.reason)
        )
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm)
    ) {
        lines.forEach { line ->
            Text(
                text = line,
                style = HearthTheme.typography.body,
                color = colors.textMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** The reason in the user's own language; the hub's own words are English and technical. */
@Composable
private fun whatWentWrong(reason: HubFailure): String = when (reason) {
    HubFailure.AddressNotFound -> stringResource(Res.string.launch_failed_address_not_found)
    is HubFailure.Upstream -> stringResource(Res.string.launch_failed_upstream, reason.statusCode)
    is HubFailure.NotJson -> stringResource(Res.string.launch_failed_not_json, reason.contentType)
    HubFailure.Unknown -> stringResource(Res.string.launch_failed_unknown)
}

@Composable
private fun HubAddressPill(hubAddress: String, reachable: Boolean) {
    val colors = HearthTheme.colors
    Row(
        modifier = Modifier
            .background(colors.surface, HearthShapes.pill)
            .border(HearthTheme.size.hairline, colors.outline, HearthShapes.pill)
            .padding(horizontal = HearthTheme.spacing.lg, vertical = HearthTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(HearthTheme.size.dot)
                .background(if (reachable) colors.primary else colors.error, HearthShapes.pill)
        )
        Text(
            text = hubAddress,
            style = HearthTheme.typography.mono,
            color = colors.textMuted
        )
    }
}

@DayNightPreviews
@Composable
private fun LaunchContentPreview(
    @PreviewParameter(LaunchUiStateProvider::class) state: LaunchUiState
) {
    HearthTheme {
        LaunchContent(state = state, onRetry = {}, onOpenTailscale = {})
    }
}
