package com.homelab.household.app.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import com.homelab.household.app.platform.LocalDragHidesKeyboard
import com.homelab.household.app.theme.HearthTheme

/**
 * Header and navigation stay put; the content passes between them. The content is handed the
 * space the bars and the window leave it, plus the screen [gutter] on both sides, and chooses how
 * it scrolls: a scrolling `Column` for forms, `LazyColumn(contentPadding = …)` for lists.
 *
 * Bars pad their own window insets, as Material 3's do; the content gets the insets only on a side
 * with no bar. [contentWindowInsets] includes the keyboard, so a form's footer rises above it.
 *
 * A tap anywhere a control doesn't take hides the keyboard. iOS has no back button to do it, and
 * its numeric keypad has no return key, so without this a field keeps the keyboard until the
 * screen is left. A field, button or chip consumes its own tap first and never reaches this.
 * Where [LocalDragHidesKeyboard] says so (iOS), a finger scrolling the screen hides it too.
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
    val focusManager = LocalFocusManager.current
    val dragHidesKeyboard = LocalDragHidesKeyboard.current
    val scrollHidesKeyboard = remember(focusManager) { ScrollHidesKeyboard(focusManager) }
    Scaffold(
        modifier = modifier
            .pointerInput(focusManager) { detectTapGestures { focusManager.clearFocus() } }
            .then(if (dragHidesKeyboard) Modifier.nestedScroll(scrollHidesKeyboard) else Modifier),
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

/**
 * Hides the keyboard when a finger scrolls the screen up or down. Only the screen's own scroll
 * counts: moving the cursor, selecting text or a long field's sideways scroll keep the keyboard,
 * and a scroll the app makes itself has no finger behind it. It consumes nothing.
 */
private class ScrollHidesKeyboard(
    private val focusManager: FocusManager,
) : NestedScrollConnection {
    override fun onPreScroll(
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (source == NestedScrollSource.UserInput && available.y != 0f) focusManager.clearFocus()
        return Offset.Zero
    }
}
