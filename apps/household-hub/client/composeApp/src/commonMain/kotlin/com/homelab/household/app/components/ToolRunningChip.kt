package com.homelab.household.app.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.homelab.household.app.theme.HearthShapes
import com.homelab.household.app.theme.HearthTheme

/**
 * A tool running, in the answer's place (design notes §6.5): "Checking your calendar…".
 *
 * It sits where the answer will land, because that is where the eye is waiting. The spinner is
 * copper rather than green: green is the app's "go", and nothing here is asking you to act.
 */
@Composable
fun ToolRunningChip(
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    Row(
        modifier =
            modifier
                .shadow(
                    elevation = HearthTheme.size.raised,
                    shape = HearthShapes.item,
                    ambientColor = colors.textPrimary.copy(alpha = 0.06f),
                    spotColor = colors.textPrimary.copy(alpha = 0.06f),
                ).background(colors.surface, HearthShapes.item)
                .padding(horizontal = HearthTheme.spacing.lg, vertical = HearthTheme.spacing.md)
                .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spinner()
        Text(text = label, style = HearthTheme.typography.label, color = colors.textMuted)
    }
}

/**
 * A quarter-arc going round a faint ring.
 *
 * With motion off it rests at its starting angle rather than stopping mid-turn: an infinite
 * transition never lets a composition go idle, and a screen test mounting it would time out
 * instead of failing with something readable (design notes §6.21).
 */
@Composable
private fun Spinner() {
    val colors = HearthTheme.colors
    val motion = HearthTheme.motion
    val angle =
        if (motion.animate) {
            val transition = rememberInfiniteTransition(label = "toolSpinner")
            val turning by transition.animateFloat(
                initialValue = 0f,
                targetValue = FULL_TURN,
                animationSpec =
                    infiniteRepeatable(
                        animation =
                            tween(
                                durationMillis = motion.barCycle.inWholeMilliseconds.toInt(),
                                easing = LinearEasing,
                            ),
                        repeatMode = RepeatMode.Restart,
                    ),
                label = "toolSpinnerAngle",
            )
            turning
        } else {
            0f
        }

    val stroke = HearthTheme.size.emphasis
    Canvas(modifier = Modifier.size(HearthTheme.size.iconSm).rotate(angle)) {
        val width = stroke.toPx()
        drawCircle(
            color = colors.secondary.copy(alpha = RING_ALPHA),
            radius = (size.minDimension - width) / 2f,
            style = Stroke(width = width),
        )
        drawArc(
            color = colors.secondary,
            startAngle = -QUARTER_TURN,
            sweepAngle = QUARTER_TURN,
            useCenter = false,
            topLeft = Offset(width / 2f, width / 2f),
            size = Size(size.width - width, size.height - width),
            style = Stroke(width = width, cap = StrokeCap.Round),
        )
    }
}

private const val FULL_TURN = 360f
private const val QUARTER_TURN = 90f
private const val RING_ALPHA = 0.22f
