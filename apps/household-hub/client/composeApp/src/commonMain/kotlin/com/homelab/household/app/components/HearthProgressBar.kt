package com.homelab.household.app.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.a11y_working
import com.homelab.household.app.theme.HearthTheme
import org.jetbrains.compose.resources.stringResource

const val HearthProgressBarTag = "HearthProgressBar"

/** Where a bar is running, which is the only thing that changes between its three uses. */
enum class HearthProgressBarWidth {
    /** A fixed track, centred: Launch and the profile picker, where it is the whole of the wait. */
    Track,

    /** Edge to edge under a header, while rows that are already on screen refresh under it. */
    FullBleed,

    /** Along the bottom edge of the button that was tapped. */
    Inset
}

/**
 * The app's one progress bar (design notes §6.21). Launch and the picker drew the same eight lines
 * of `LinearProgressIndicator` byte for byte; this is that, named, with the two other widths the
 * waiting patterns need.
 *
 * It announces itself rather than being decorative: a bar is the only sign the app gives that a
 * call is in flight, so a screen reader has to hear about it too.
 *
 * When `motion.animate` is false it draws **determinate at a fixed fraction** instead. An
 * indeterminate bar animates forever, and a composition holding one never goes idle — a test under
 * it would time out rather than fail.
 */
@Composable
fun HearthProgressBar(
    modifier: Modifier = Modifier,
    width: HearthProgressBarWidth = HearthProgressBarWidth.Track,
    color: Color = HearthTheme.colors.primary,
    trackColor: Color = if (width == HearthProgressBarWidth.Track) HearthTheme.colors.outline else Color.Transparent
) {
    val size = HearthTheme.size
    val working = stringResource(Res.string.a11y_working)

    val shaped = when (width) {
        HearthProgressBarWidth.Track -> modifier.width(size.progressTrack)
        HearthProgressBarWidth.FullBleed, HearthProgressBarWidth.Inset -> modifier.fillMaxWidth()
    }
        .height(size.progressHeight)
        .testTag(HearthProgressBarTag)
        .semantics {
            stateDescription = working
            liveRegion = LiveRegionMode.Polite
        }

    // Full bleed runs to both screen edges, where a rounded end would read as a mistake.
    val cap = if (width == HearthProgressBarWidth.FullBleed) StrokeCap.Butt else StrokeCap.Round
    val gap = HearthTheme.spacing.none

    if (HearthTheme.motion.animate) {
        LinearProgressIndicator(
            modifier = shaped,
            color = color,
            trackColor = trackColor,
            strokeCap = cap,
            gapSize = gap
        )
    } else {
        LinearProgressIndicator(
            progress = { RESTING_FRACTION },
            modifier = shaped,
            color = color,
            trackColor = trackColor,
            strokeCap = cap,
            gapSize = gap,
            drawStopIndicator = {}
        )
    }
}

/** Where the bar is frozen with motion off: far enough along to be visibly a bar, not a full one. */
private const val RESTING_FRACTION = 0.35f
