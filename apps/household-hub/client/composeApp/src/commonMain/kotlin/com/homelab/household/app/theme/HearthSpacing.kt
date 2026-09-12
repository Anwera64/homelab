package com.homelab.household.app.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Space between things, on a 4dp grid (design notes §3). Read it from the theme —
 * `HearthTheme.spacing.lg` — so a later size class can hand a screen a different scale without
 * the screen knowing.
 *
 * A screen picks a step; it never writes a dp of its own. `DesignSystemTokenTest` in `:shared`
 * fails the build on a raw dp anywhere in `composeApp/commonMain` outside this package.
 */
@Immutable
data class HearthSpacing(
    /** An explicit zero, for the side of a [androidx.compose.foundation.layout.PaddingValues]. */
    val none: Dp = 0.dp,
    /** The one half-step: hairline insets, like the vertical padding of a tool record. */
    val xxs: Dp = 2.dp,
    /** Tightest gap: chip padding, a nav item's inset. */
    val xs: Dp = 4.dp,
    /** The default inline gap: icon to text, label to field, line to line. */
    val sm: Dp = 8.dp,
    /** Inside a card, and a control's inner padding. */
    val md: Dp = 12.dp,
    /** Between related blocks; a field's and a pill's padding. */
    val lg: Dp = 16.dp,
    /** Screen gutter and card padding. */
    val xl: Dp = 20.dp,
    /** Between sections. */
    val xxl: Dp = 24.dp,
    /** Around a fixed-shape screen's hero. */
    val xxxl: Dp = 32.dp,
    /** The outer padding of a full-screen state. */
    val huge: Dp = 40.dp
)

/** The phone scale, and the only one there is until tablets land. */
val DefaultSpacing = HearthSpacing()
