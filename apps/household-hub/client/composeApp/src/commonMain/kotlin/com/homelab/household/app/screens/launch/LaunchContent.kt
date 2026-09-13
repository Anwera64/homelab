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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.homelab.household.app.components.HearthScaffold
import com.homelab.household.app.components.PrimaryButton
import com.homelab.household.app.components.SecondaryButton
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.icons.HearthIconImage
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.app_name
import com.homelab.household.app.resources.launch_action_description
import com.homelab.household.app.resources.launch_checking
import com.homelab.household.app.resources.launch_no_route_chip
import com.homelab.household.app.resources.launch_no_route_detail
import com.homelab.household.app.resources.launch_no_route_title
import com.homelab.household.app.resources.launch_not_found_chip
import com.homelab.household.app.resources.launch_not_found_detail
import com.homelab.household.app.resources.launch_not_found_title
import com.homelab.household.app.resources.launch_offline_hint
import com.homelab.household.app.resources.launch_open_tailscale
import com.homelab.household.app.resources.launch_retry
import com.homelab.household.app.resources.launch_retrying_in
import com.homelab.household.app.resources.launch_retrying_seconds
import com.homelab.household.app.resources.launch_unknown_chip
import com.homelab.household.app.resources.launch_unknown_detail
import com.homelab.household.app.resources.launch_unknown_title
import com.homelab.household.app.resources.launch_upstream_chip
import com.homelab.household.app.resources.launch_upstream_detail
import com.homelab.household.app.resources.launch_upstream_title
import com.homelab.household.app.resources.launch_web_page_chip
import com.homelab.household.app.resources.launch_web_page_detail
import com.homelab.household.app.resources.launch_web_page_title
import com.homelab.household.app.theme.DayNightPreviews
import com.homelab.household.app.theme.HearthShapes
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.presentation.launch.HubFailure
import com.homelab.household.presentation.launch.HubStatus
import com.homelab.household.presentation.launch.LaunchUiState
import org.jetbrains.compose.resources.stringResource

/**
 * The first screen a signed-out phone shows. Fixed shape; the offline frame scrolls only when it
 * doesn't fit.
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
    when (val status = state.status) {
        HubStatus.Checking -> CheckingContent(hubAddress = state.hubAddress, modifier = modifier)
        is HubStatus.Unavailable -> OfflineContent(
            reason = status.reason,
            hubAddress = state.hubAddress,
            retryInSeconds = state.retryInSeconds,
            onRetry = onRetry,
            onOpenTailscale = onOpenTailscale,
            modifier = modifier
        )
    }
}

/**
 * The splash: the hub is being asked. A useful answer moves the user on from here, so this is
 * the only frame they see on the way to signing in or setting up.
 */
@Composable
private fun CheckingContent(hubAddress: String, modifier: Modifier) {
    val colors = HearthTheme.colors

    HearthScaffold(modifier = modifier, gutter = HearthTheme.spacing.huge) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(vertical = HearthTheme.spacing.huge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(
                HearthTheme.spacing.xxxl,
                Alignment.CenterVertically
            )
        ) {
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

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm)
            ) {
                Text(
                    text = stringResource(Res.string.app_name),
                    style = HearthTheme.typography.hero,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(Res.string.launch_checking),
                    style = HearthTheme.typography.body,
                    color = colors.textMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = HearthTheme.size.readingWidth)
                )
            }

            HubAddressPill(hubAddress = hubAddress)

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
    }
}

/**
 * The hub couldn't be read. Not an error page: a hub that's off, one still starting, a Wi-Fi
 * sign-in page and a phone away from home without Tailscale all land here before anything is
 * typed, so it says which it was, asks again by itself, and offers the ways back.
 */
@Composable
private fun OfflineContent(
    reason: HubFailure,
    hubAddress: String,
    retryInSeconds: Int?,
    onRetry: () -> Unit,
    onOpenTailscale: () -> Unit,
    modifier: Modifier
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography
    val words = wordsFor(reason)

    HearthScaffold(modifier = modifier, gutter = HearthTheme.spacing.xxxl) { padding ->
        // Fixed shape by design, but a short phone or a large font would clip the buttons: it scrolls
        // only then, and stays centred otherwise.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(vertical = HearthTheme.spacing.huge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xxl, Alignment.CenterVertically)
        ) {
            OfflineTile()

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md)
            ) {
                Text(
                    text = words.title,
                    style = type.title,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = words.detail,
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
                        text = hubAddress,
                        style = type.monoSm,
                        color = colors.textMuted,
                        modifier = Modifier.weight(1f)
                    )
                    HearthChip(label = words.chip, variant = ChipVariant.Error)
                }
                retryInSeconds?.let { seconds ->
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
}

/** What the offline screen says about one reason. */
private class OfflineWords(val title: String, val detail: String, val chip: String)

/** The reason in the user's own language; the hub's own words are English and technical. */
@Composable
private fun wordsFor(reason: HubFailure): OfflineWords = when (reason) {
    HubFailure.NoRoute -> OfflineWords(
        title = stringResource(Res.string.launch_no_route_title),
        detail = stringResource(Res.string.launch_no_route_detail),
        chip = stringResource(Res.string.launch_no_route_chip)
    )
    is HubFailure.Upstream -> OfflineWords(
        title = stringResource(Res.string.launch_upstream_title),
        detail = stringResource(Res.string.launch_upstream_detail, reason.statusCode),
        chip = stringResource(Res.string.launch_upstream_chip, reason.statusCode)
    )
    is HubFailure.NotJson -> OfflineWords(
        title = stringResource(Res.string.launch_web_page_title),
        detail = stringResource(Res.string.launch_web_page_detail),
        chip = stringResource(Res.string.launch_web_page_chip)
    )
    HubFailure.AddressNotFound -> OfflineWords(
        title = stringResource(Res.string.launch_not_found_title),
        detail = stringResource(Res.string.launch_not_found_detail),
        chip = stringResource(Res.string.launch_not_found_chip)
    )
    HubFailure.Unknown -> OfflineWords(
        title = stringResource(Res.string.launch_unknown_title),
        detail = stringResource(Res.string.launch_unknown_detail),
        chip = stringResource(Res.string.launch_unknown_chip)
    )
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
private fun HubAddressPill(hubAddress: String) {
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
                .background(colors.primary, HearthShapes.pill)
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
