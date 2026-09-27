package com.homelab.household.app.screens.calendarprovider

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.homelab.household.app.components.HearthScaffold
import com.homelab.household.app.components.HearthTopBar
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.icons.HearthIconImage
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.calendar_apple
import com.homelab.household.app.resources.calendar_apple_caption
import com.homelab.household.app.resources.calendar_back
import com.homelab.household.app.resources.calendar_google
import com.homelab.household.app.resources.calendar_google_caption
import com.homelab.household.app.resources.calendar_other
import com.homelab.household.app.resources.calendar_other_caption
import com.homelab.household.app.resources.calendar_provider_detail
import com.homelab.household.app.resources.calendar_provider_note
import com.homelab.household.app.resources.calendar_provider_title
import com.homelab.household.app.theme.HearthShapes
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.app.theme.PreviewDayNight
import com.homelab.household.domain.model.CalendarProvider
import org.jetbrains.compose.resources.stringResource

/**
 * Where your calendar lives: the first step of connecting one (board "pick provider"). Nothing here
 * waits on the hub, so it has no ViewModel; picking one opens the details that provider needs.
 */
@Composable
fun CalendarProviderScreen(
    onBack: () -> Unit,
    onPick: (CalendarProvider) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography

    HearthScaffold(
        modifier = modifier,
        header = { HearthTopBar(onBack = onBack, backDescription = stringResource(Res.string.calendar_back)) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding),
            verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xxl),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm)) {
                Text(stringResource(Res.string.calendar_provider_title), style = type.hero, color = colors.textPrimary)
                Text(stringResource(Res.string.calendar_provider_detail), style = type.body, color = colors.textMuted)
            }

            Column(verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md)) {
                CalendarProvider.entries.forEach { provider ->
                    ProviderCard(
                        provider = provider,
                        // Google's CalDAV only takes a Google sign-in, which the hub can't do yet.
                        enabled = provider != CalendarProvider.GOOGLE,
                        onClick = { onPick(provider) },
                    )
                }
            }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceAlt, HearthShapes.item)
                        .padding(HearthTheme.spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md),
                verticalAlignment = Alignment.Top,
            ) {
                HearthIconImage(
                    icon = HearthIcon.SecretLocked,
                    contentDescription = null,
                    size = HearthTheme.size.iconMd,
                    tint = colors.textMuted,
                )
                Text(stringResource(Res.string.calendar_provider_note), style = type.caption, color = colors.textMuted)
            }
        }
    }
}

@Composable
private fun ProviderCard(
    provider: CalendarProvider,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = HearthShapes.bento,
        color = colors.surface,
        contentColor = colors.textPrimary,
    ) {
        Row(
            modifier = Modifier.padding(HearthTheme.spacing.xl),
            horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(HearthTheme.size.touchTarget).background(colors.canvas, HearthShapes.item),
                contentAlignment = Alignment.Center,
            ) {
                CalendarProviderMark(provider = provider)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xs),
            ) {
                Text(providerName(provider), style = type.bodyStrong, color = colors.textPrimary)
                Text(providerCaption(provider), style = type.caption, color = colors.textMuted)
            }
            if (enabled) {
                HearthIconImage(
                    icon = HearthIcon.ChevronRight,
                    contentDescription = null,
                    size = HearthTheme.size.iconMd,
                    tint = colors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun providerCaption(provider: CalendarProvider): String =
    when (provider) {
        CalendarProvider.APPLE -> stringResource(Res.string.calendar_apple_caption)
        CalendarProvider.GOOGLE -> stringResource(Res.string.calendar_google_caption)
        CalendarProvider.OTHER -> stringResource(Res.string.calendar_other_caption)
    }

@PreviewDayNight
@Composable
private fun CalendarProviderScreenPreview() {
    HearthTheme {
        CalendarProviderScreen(onBack = {}, onPick = {})
    }
}
