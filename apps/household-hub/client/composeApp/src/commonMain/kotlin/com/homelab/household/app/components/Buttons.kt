package com.homelab.household.app.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.icons.HearthIconImage
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.a11y_working
import com.homelab.household.app.theme.HearthShapes
import com.homelab.household.app.theme.HearthTheme
import org.jetbrains.compose.resources.stringResource

// Buttons deliberately have no `enabled` parameter: a primary action is never dimmed,
// it explains what's missing when tapped (design notes §2).
//
// `busy` is not a way back to `enabled`. A working button keeps its colour, its shadow and its
// place in the accessibility tree; all that changes is the label the caller passes — the verb in
// progress — a bar along the bottom edge, and that a second tap goes nowhere.

// Both come from the theme, so they are read inside a composition rather than at file scope.
private val buttonMinHeight: Dp
    @Composable
    @ReadOnlyComposable
    get() = HearthTheme.size.control

private val buttonPadding: PaddingValues
    @Composable
    @ReadOnlyComposable
    get() = PaddingValues(horizontal = HearthTheme.spacing.xxl, vertical = HearthTheme.spacing.lg)

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: HearthIcon? = null,
    busy: Boolean = false
) {
    val colors = HearthTheme.colors
    WithBusyBar(modifier = modifier, busy = busy, barColor = colors.onPrimary) {
        Button(
            onClick = { if (!busy) onClick() },
            modifier = Modifier
                .heightIn(min = buttonMinHeight)
                .shadow(
                    elevation = HearthTheme.size.raised,
                    shape = HearthShapes.button,
                    ambientColor = colors.primary.copy(alpha = 0.30f),
                    spotColor = colors.primary.copy(alpha = 0.30f)
                )
                .busySemantics(busy),
            shape = HearthShapes.button,
            colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
            contentPadding = buttonPadding
        ) {
            ButtonContent(text, icon)
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: HearthIcon? = null,
    busy: Boolean = false
) {
    OutlinedActionButton(
        text, onClick, modifier, icon, busy, HearthTheme.colors.outline, HearthTheme.colors.textMuted
    )
}

/** Outlined in error red rather than filled: destructive, but never the page's purpose. */
@Composable
fun DestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: HearthIcon? = null,
    busy: Boolean = false
) {
    OutlinedActionButton(
        text, onClick, modifier, icon, busy, HearthTheme.colors.error, HearthTheme.colors.error
    )
}

@Composable
private fun OutlinedActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    icon: HearthIcon?,
    busy: Boolean,
    borderColor: Color,
    contentColor: Color
) {
    WithBusyBar(modifier = modifier, busy = busy, barColor = contentColor) {
        OutlinedButton(
            onClick = { if (!busy) onClick() },
            modifier = Modifier.heightIn(min = buttonMinHeight).busySemantics(busy),
            shape = HearthShapes.button,
            border = BorderStroke(HearthTheme.size.hairline, borderColor),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
            contentPadding = buttonPadding
        ) {
            ButtonContent(text, icon)
        }
    }
}

/**
 * The button, with the bar laid over its bottom edge while it works.
 *
 * The bar is a sibling rather than part of the button's content, because the content sits inside
 * [buttonPadding] and the bar belongs on the edge. `matchParentSize` gives the overlay exactly the
 * button's measured size, and the clip keeps the bar inside the button's corners.
 */
@Composable
private fun WithBusyBar(
    modifier: Modifier,
    busy: Boolean,
    barColor: Color,
    button: @Composable () -> Unit
) {
    if (!busy) {
        Box(modifier) { button() }
        return
    }
    Box(modifier) {
        button()
        BusyBar(barColor)
    }
}

@Composable
private fun BoxScope.BusyBar(barColor: Color) {
    Box(
        modifier = Modifier.matchParentSize().clip(HearthShapes.button),
        contentAlignment = Alignment.BottomCenter
    ) {
        HearthProgressBar(width = HearthProgressBarWidth.Inset, color = barColor)
    }
}

/**
 * What a screen reader hears instead of a spinner it cannot see. Deliberately not `enabled = false`:
 * that would dim the button and drop it out of the tree, and a button that vanishes mid-tap is
 * worse than one that says what it is doing.
 */
@Composable
private fun Modifier.busySemantics(busy: Boolean): Modifier {
    if (!busy) return this
    val working = stringResource(Res.string.a11y_working)
    return semantics { stateDescription = working }
}

@Composable
private fun RowScope.ButtonContent(text: String, icon: HearthIcon?) {
    if (icon != null) {
        HearthIconImage(icon = icon, contentDescription = null, active = true, size = HearthTheme.size.iconMd)
        Spacer(Modifier.width(HearthTheme.spacing.sm))
    }
    Text(text, style = HearthTheme.typography.bodyStrong)
}
