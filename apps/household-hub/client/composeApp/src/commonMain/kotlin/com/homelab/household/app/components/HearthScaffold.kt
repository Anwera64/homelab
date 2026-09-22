package com.homelab.household.app.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import com.homelab.household.app.theme.HearthTheme

/**
 * Header and navigation stay put; the content passes between them. The content is handed the
 * space the bars and the window leave it, plus the screen [gutter] on both sides, and chooses how
 * it scrolls: a scrolling `Column` for forms, `LazyColumn(contentPadding = …)` for lists.
 *
 * Bars pad their own window insets, as Material 3's do; the content gets the insets only on a side
 * with no bar. [contentWindowInsets] includes the keyboard, so a form's footer rises above it.
 */
@Composable
fun HearthScaffold(
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    gutter: Dp = HearthTheme.spacing.xl,
    contentWindowInsets: WindowInsets = WindowInsets.safeDrawing,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = header,
        bottomBar = bottomBar,
        containerColor = HearthTheme.colors.canvas,
        contentWindowInsets = contentWindowInsets,
    ) { bars ->
        val layoutDirection = LocalLayoutDirection.current
        content(
            PaddingValues(
                start = bars.calculateStartPadding(layoutDirection) + gutter,
                top = bars.calculateTopPadding(),
                end = bars.calculateEndPadding(layoutDirection) + gutter,
                bottom = bars.calculateBottomPadding(),
            ),
        )
    }
}
