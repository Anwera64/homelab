package com.homelab.household.app.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How big things are (design notes §3). Icons climb in 4dp steps; anything larger sits on 8dp.
 * Read it from the theme — `HearthTheme.size.iconMd`.
 *
 * Strokes and elevation are deliberately off the grid: a 1dp border and a shadow's blur are not
 * layout, and rounding them to 4dp would only make them wrong.
 */
@Immutable
data class HearthSizes(
    /** Inline with text: chips, tool records, a field's error. */
    val iconSm: Dp = 16.dp,
    /** Button and composer icons. */
    val iconMd: Dp = 20.dp,
    /** Navigation, the raised +, and the icon set's own drawing grid. */
    val iconLg: Dp = 24.dp,
    /** On an empty state's tile. */
    val iconXl: Dp = 28.dp,
    /** On a [tile]. */
    val iconXxl: Dp = 32.dp,
    /** On a [tileHero]. */
    val iconHero: Dp = 40.dp,
    /** The floor for anything tappable, and a text field's height. */
    val touchTarget: Dp = 48.dp,
    /** Button height, and the raised +. */
    val control: Dp = 56.dp,
    /** The soft tile an empty state's or a launch state's icon sits on. */
    val tile: Dp = 64.dp,
    /** Launch's ready and offline tile. */
    val tileHero: Dp = 80.dp,
    /** A colour to pick on first run; a ring around it in the rest of the touch target marks the choice. */
    val swatch: Dp = 40.dp,
    /** A face on "Who's here?". */
    val avatarHero: Dp = 96.dp,
    /** One of the six dots over the PIN pad. */
    val pinDot: Dp = 16.dp,
    /**
     * One caption line, held open whether or not there are words in it. A fixed-shape screen
     * cannot let a line arriving shove what is under it, so the PIN pad reserves this much.
     */
    val statusLine: Dp = 24.dp,
    /** Borders and dividers. */
    val hairline: Dp = 1.dp,
    /** A field's error edge. */
    val emphasis: Dp = 2.dp,
    /** How wide centred prose is allowed to run before it stops being readable. */
    val readingWidth: Dp = 288.dp,
    /** Launch's indeterminate bar: how wide it runs, and how thick. */
    val progressTrack: Dp = 160.dp,
    val progressHeight: Dp = 4.dp,
    /** The hub address pill's status dot. */
    val dot: Dp = 8.dp,
    /** Shadow blur under the primary button and the raised +. */
    val raised: Dp = 10.dp,
)

/** The phone scale, and the only one there is until tablets land. */
val DefaultSizes = HearthSizes()
