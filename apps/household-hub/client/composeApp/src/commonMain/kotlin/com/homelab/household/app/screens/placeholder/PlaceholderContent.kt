package com.homelab.household.app.screens.placeholder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.home_detail
import com.homelab.household.app.resources.home_title
import com.homelab.household.app.theme.DayNightPreviews
import com.homelab.household.app.theme.HearthTheme
import org.jetbrains.compose.resources.stringResource

/**
 * A destination that navigation already reaches and a later slice still has to build.
 * Replace the entry in `AppNavHost` with the real screen when that slice lands.
 */
@Composable
fun PlaceholderContent(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(HearthTheme.spacing.huge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm, Alignment.CenterVertically),
    ) {
        Text(
            text = title,
            style = HearthTheme.typography.hero,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = detail,
            style = HearthTheme.typography.body,
            color = colors.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = HearthTheme.size.readingWidth),
        )
    }
}

@DayNightPreviews
@Composable
private fun PlaceholderContentPreview() {
    HearthTheme {
        PlaceholderContent(
            title = stringResource(Res.string.home_title),
            detail = stringResource(Res.string.home_detail),
        )
    }
}
