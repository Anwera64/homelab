package com.homelab.household.app.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.a11y_loading
import com.homelab.household.app.theme.HearthShapes
import com.homelab.household.app.theme.HearthTheme
import org.jetbrains.compose.resources.stringResource

const val SkeletonGroupTag = "SkeletonGroup"

/**
 * The blocks that stand where an answer will land while the hub is asked for it (design notes
 * §6.21, pattern 2). Real chrome draws at once; only the part that depends on the answer breathes.
 *
 * The group is what a screen reader hears — once, politely. The blocks inside it are shapes with
 * nothing to say yet, so they are cleared out of the semantics tree entirely rather than read
 * aloud one after another.
 */
@Composable
fun SkeletonGroup(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(HearthTheme.spacing.md),
    content: @Composable ColumnScope.() -> Unit,
) {
    val loading = stringResource(Res.string.a11y_loading)
    Column(
        modifier =
            modifier
                .testTag(SkeletonGroupTag)
                .semantics {
                    stateDescription = loading
                    liveRegion = LiveRegionMode.Polite
                },
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/**
 * A line of text that hasn't arrived. [widthFraction] is how much of the row it will fill — a
 * skeleton reads as a wait rather than a grid when its lines are not all the same length.
 */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    height: Dp = HearthTheme.spacing.lg,
    widthFraction: Float = 1f,
    shape: Shape = HearthShapes.skeleton,
    position: Int = 0,
) {
    Skeleton(modifier.fillMaxWidth(widthFraction).height(height), shape, position)
}

/** An avatar that hasn't arrived. */
@Composable
fun SkeletonCircle(
    modifier: Modifier = Modifier,
    size: Dp = HearthTheme.size.touchTarget,
    position: Int = 0,
) {
    Skeleton(modifier.size(size), CircleShape, position)
}

@Composable
private fun Skeleton(
    modifier: Modifier = Modifier,
    shape: Shape,
    position: Int,
) {
    Box(
        modifier =
            modifier
                .alpha(breath(position))
                .background(HearthTheme.colors.outlineSoft, shape)
                .clearAndSetSemantics {},
    )
}

/**
 * The breath: dim to full and back, forever, over `motion.breathe`. [position] sets it a beat
 * behind its neighbour, so a row of blocks arrives as one thing rather than blinking in unison.
 *
 * With motion off it is a constant instead — not a first frame but the *full* one, so a skeleton
 * in a test or a screenshot reads as a solid block rather than a half-faded one. It has to be a
 * constant and not a stopped animation: an infinite transition keeps the composition from ever
 * going idle, and a UI test under it would hang rather than fail.
 */
@Composable
private fun breath(position: Int): Float {
    val motion = HearthTheme.motion
    if (!motion.animate) return FULL

    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = DIM,
        targetValue = FULL,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = motion.breathe.inWholeMilliseconds.toInt()),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset((motion.breatheStagger * position).inWholeMilliseconds.toInt()),
            ),
        label = "skeletonAlpha",
    )
    return alpha
}

private const val DIM = 0.5f
private const val FULL = 1f
