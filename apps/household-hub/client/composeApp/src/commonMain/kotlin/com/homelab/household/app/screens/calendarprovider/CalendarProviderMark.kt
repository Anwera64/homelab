package com.homelab.household.app.screens.calendarprovider

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.icons.HearthIconImage
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.calendar_apple
import com.homelab.household.app.resources.calendar_apple_glyph
import com.homelab.household.app.resources.calendar_google
import com.homelab.household.app.resources.calendar_other
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.domain.model.CalendarProvider
import org.jetbrains.compose.resources.stringResource

/**
 * Each provider's mark, the same wherever a calendar is named: the picker, the details screen's
 * header and the profile row. Apple wears its glyph; the other two a Hearth icon.
 */
@Composable
fun CalendarProviderMark(
    provider: CalendarProvider,
    modifier: Modifier = Modifier,
) {
    when (provider) {
        CalendarProvider.APPLE -> {
            Text(
                stringResource(Res.string.calendar_apple_glyph),
                modifier = modifier,
                style = HearthTheme.typography.glyphMd,
            )
        }

        CalendarProvider.GOOGLE -> {
            HearthIconImage(
                icon = HearthIcon.Schedule,
                contentDescription = null,
                size = HearthTheme.size.iconLg,
                tint = HearthTheme.colors.textMuted,
                modifier = modifier,
            )
        }

        CalendarProvider.OTHER -> {
            HearthIconImage(
                icon = HearthIcon.HubOnline,
                contentDescription = null,
                size = HearthTheme.size.iconLg,
                tint = HearthTheme.colors.textMuted,
                modifier = modifier,
            )
        }
    }
}

/** The provider's name as the member knows it. */
@Composable
fun providerName(provider: CalendarProvider): String =
    when (provider) {
        CalendarProvider.APPLE -> stringResource(Res.string.calendar_apple)
        CalendarProvider.GOOGLE -> stringResource(Res.string.calendar_google)
        CalendarProvider.OTHER -> stringResource(Res.string.calendar_other)
    }
