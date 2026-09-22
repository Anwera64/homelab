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
import androidx.compose.runtime.Immutable
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
import com.homelab.household.app.theme.HearthColors
import com.homelab.household.app.theme.HearthShapes
import com.homelab.household.app.theme.HearthTheme
import org.jetbrains.compose.resources.stringResource

// Buttons deliberately have no `enabled` parameter: a primary action is never dimmed,
// it explains what's missing when tapped (design notes §2).
//
// `busy` is not a way back to `enabled`. A working button keeps its colour, its shadow and its
// place in the accessibility tree; all that changes is the label the caller passes — the verb in
// progress — a bar along the bottom edge, and that a second tap goes nowhere.
//
// `busyDescription` is what a screen reader hears instead of the bar it cannot see. Each action
// names its own wait; the generic "Working" is only the fallback for a caller with nothing better
// to say.

/**
 * The bar along a working button (design notes §6.21). Its track is not a gap — it is the button's
 * own surface at a fraction, so the bar reads as part of the button rather than a strip laid over
 * it. That is also why these are a value rather than something a call site picks: a filled button
 * and an outlined one need different roles, and taking either from the button's `contentColor`
 * gives the secondary a grey bar on a grey button.
 *
 * The two fractions are deliberately plain numbers and not palette tokens. A ghost tint is tuned
 * per palette because it has to land on the same perceived edge in both (design notes §2); a track
 * at a fraction of its own bar is relative to whatever that bar already is, so it doesn't drift.
 */
@Immutable
internal data class BusyBar(
    val bar: Color,
    val track: Color,
) {
    companion object {
        fun filled(colors: HearthColors) = BusyBar(colors.onPrimary, colors.onPrimary.copy(alpha = FILLED_TRACK))

        fun outlined(colors: HearthColors) = BusyBar(colors.primary, colors.outlineSoft)

        fun destructive(colors: HearthColors) = BusyBar(colors.error, colors.error.copy(alpha = DESTRUCTIVE_TRACK))

        private const val FILLED_TRACK = 0.24f
        private const val DESTRUCTIVE_TRACK = 0.18f
    }
}

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
    busy: Boolean = false,
    busyDescription: String? = null,
) {
    val colors = HearthTheme.colors
    WithBusyBar(modifier = modifier, busy = busy, busyBar = BusyBar.filled(colors)) {
        Button(
            onClick = { if (!busy) onClick() },
            modifier =
                Modifier
                    .heightIn(min = buttonMinHeight)
                    .shadow(
                        elevation = HearthTheme.size.raised,
                        shape = HearthShapes.button,
                        ambientColor = colors.primary.copy(alpha = 0.30f),
                        spotColor = colors.primary.copy(alpha = 0.30f),
                    ).busySemantics(busy, busyDescription),
            shape = HearthShapes.button,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                ),
            contentPadding = buttonPadding,
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
    busy: Boolean = false,
    busyDescription: String? = null,
) {
    val colors = HearthTheme.colors
    OutlinedActionButton(
        text,
        onClick,
        modifier,
        icon,
        busy,
        busyDescription,
        colors.outline,
        colors.textMuted,
        BusyBar.outlined(colors),
    )
}

/** Outlined in error red rather than filled: destructive, but never the page's purpose. */
@Composable
fun DestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: HearthIcon? = null,
    busy: Boolean = false,
    busyDescription: String? = null,
) {
    val colors = HearthTheme.colors
    OutlinedActionButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        icon = icon,
        busy = busy,
        busyDescription = busyDescription,
        borderColor = colors.error,
        contentColor = colors.error,
        busyBar = BusyBar.destructive(colors),
    )
}

@Composable
private fun OutlinedActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: HearthIcon?,
    busy: Boolean,
    busyDescription: String?,
    borderColor: Color,
    contentColor: Color,
    busyBar: BusyBar,
) {
    WithBusyBar(modifier = modifier, busy = busy, busyBar = busyBar) {
        OutlinedButton(
            onClick = { if (!busy) onClick() },
            modifier =
                Modifier
                    .heightIn(min = buttonMinHeight)
                    .busySemantics(busy, busyDescription),
            shape = HearthShapes.button,
            border = BorderStroke(HearthTheme.size.hairline, borderColor),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
            contentPadding = buttonPadding,
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
 *
 * **[modifier] sizes this box, so its minimum constraints have to reach the button inside it.**
 * Without `propagateMinConstraints` the caller's `fillMaxWidth()` widened the box and left the
 * button wrap-content in the corner of it — every full-width button in the app was a full-width
 * box around a button hugging its label, shadow and all. Propagating the *minimum* is what keeps
 * both readings honest: a caller who asked for a width gets it, and one who asked for nothing
 * still wraps, because then the minimum is zero.
 *
 * One box rather than one per branch, so flipping [busy] does not discard the button and build a
 * new one — that would reset its interaction state mid-tap, at the worst possible moment.
 */
@Composable
private fun WithBusyBar(
    modifier: Modifier = Modifier,
    busy: Boolean,
    busyBar: BusyBar,
    button: @Composable () -> Unit,
) {
    Box(modifier = modifier, propagateMinConstraints = true) {
        button()
        if (busy) BusyBarOverlay(busyBar)
    }
}

@Composable
private fun BoxScope.BusyBarOverlay(busyBar: BusyBar) {
    Box(
        modifier = Modifier.matchParentSize().clip(HearthShapes.button),
        contentAlignment = Alignment.BottomCenter,
    ) {
        HearthProgressBar(
            width = HearthProgressBarWidth.Inset,
            color = busyBar.bar,
            trackColor = busyBar.track,
        )
    }
}

/**
 * What a screen reader hears instead of a bar it cannot see. Deliberately not `enabled = false`:
 * that would dim the button and drop it out of the tree, and a button that vanishes mid-tap is
 * worse than one that says what it is doing.
 */
@Composable
private fun Modifier.busySemantics(
    busy: Boolean,
    description: String?,
): Modifier {
    if (!busy) return this
    val announcement = description ?: stringResource(Res.string.a11y_working)
    return semantics { stateDescription = announcement }
}

@Composable
private fun RowScope.ButtonContent(
    text: String,
    icon: HearthIcon?,
) {
    if (icon != null) {
        HearthIconImage(
            icon = icon,
            contentDescription = null,
            active = true,
            size = HearthTheme.size.iconMd,
        )
        Spacer(Modifier.width(HearthTheme.spacing.sm))
    }
    Text(text, style = HearthTheme.typography.bodyStrong)
}
