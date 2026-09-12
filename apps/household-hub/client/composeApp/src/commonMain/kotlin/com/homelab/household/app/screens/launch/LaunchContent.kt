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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.sp
import com.homelab.household.app.components.PrimaryButton
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.app_name
import com.homelab.household.app.resources.launch_checking
import com.homelab.household.app.resources.launch_failed_address_not_found
import com.homelab.household.app.resources.launch_failed_not_json
import com.homelab.household.app.resources.launch_failed_title
import com.homelab.household.app.resources.launch_failed_unknown
import com.homelab.household.app.resources.launch_failed_upstream
import com.homelab.household.app.resources.launch_first_run_detail
import com.homelab.household.app.resources.launch_first_run_title
import com.homelab.household.app.resources.launch_member_count
import com.homelab.household.app.resources.launch_ready
import com.homelab.household.app.resources.launch_retry
import com.homelab.household.app.resources.launch_unreachable_detail
import com.homelab.household.app.resources.launch_unreachable_title
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.icons.HearthIconImage
import com.homelab.household.app.resources.launch_action_description
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
    modifier: Modifier = Modifier
) {
    val colors = HearthTheme.colors
    val status = state.status
    val unreachable = status is HubStatus.Unreachable || status is HubStatus.Failed

    Column(
        modifier = modifier.fillMaxSize().background(colors.canvas).padding(HearthTheme.spacing.huge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xxxl, Alignment.CenterVertically)
    ) {
        if (unreachable) {
            Box(
                modifier = Modifier.size(HearthTheme.size.tile).background(colors.errorContainer, HearthShapes.tile),
                contentAlignment = Alignment.Center
            ) {
                HearthIconImage(
                    HearthIcon.HubOffline,
                    null,
                    size = HearthTheme.size.iconXxl,
                    tint = colors.onErrorContainer
                )
            }
        } else {
            Box(
                modifier = Modifier.size(HearthTheme.size.tileHero).background(colors.primary, HearthShapes.tile),
                contentAlignment = Alignment.Center
            ) {
                HearthIconImage(HearthIcon.Household, null, size = HearthTheme.size.iconHero, tint = colors.onPrimary)
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm)
        ) {
            Text(
                text = stringResource(
                    if (unreachable) Res.string.launch_unreachable_title else Res.string.app_name
                ),
                fontFamily = HearthTheme.fonts.outfit,
                fontSize = if (unreachable) 23.sp else 27.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                textAlign = TextAlign.Center
            )
            StatusLines(status = status, modifier = Modifier.widthIn(max = HearthTheme.size.readingWidth))
        }

        HubAddressPill(hubAddress = state.hubAddress, reachable = !unreachable)

        if (status is HubStatus.Checking) {
            Box(
                modifier = Modifier
                    .width(HearthTheme.size.progressTrack)
                    .height(HearthTheme.size.progressHeight)
                    .background(colors.outline, HearthShapes.pill),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    Modifier
                        .width(HearthTheme.size.progressThumb)
                        .height(HearthTheme.size.progressHeight)
                        .background(colors.primary, HearthShapes.pill)
                )
            }
            Text(
                text = stringResource(Res.string.launch_action_description),
                style = HearthTheme.typography.mono,
                fontSize = 10.5.sp,
                color = colors.textMuted
            )
        }

        if (unreachable) {
            PrimaryButton(
                text = stringResource(Res.string.launch_retry),
                onClick = onRetry,
                icon = HearthIcon.Retry,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun StatusLines(status: HubStatus, modifier: Modifier = Modifier) {
    val colors = HearthTheme.colors
    val lines = when (status) {
        HubStatus.Checking -> listOf(stringResource(Res.string.launch_checking))
        is HubStatus.Ready -> listOf(
            stringResource(Res.string.launch_ready),
            pluralStringResource(Res.plurals.launch_member_count, status.memberCount, status.memberCount)
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
                fontFamily = HearthTheme.fonts.inter,
                fontSize = 14.5.sp,
                lineHeight = 22.sp,
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
            fontSize = 11.5.sp,
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
        LaunchContent(state = state, onRetry = {})
    }
}
