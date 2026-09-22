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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.conversation_thinking
import com.homelab.household.app.theme.HearthMotion
import com.homelab.household.app.theme.HearthTheme
import org.jetbrains.compose.resources.stringResource

const val THINKING_DOTS_TAG = "ThinkingDots"

/**
 * The wait between sending a question and the first word of the answer.
 *
 * The longest silence in the app: the hub may be loading a model into memory before it can begin,
 * and the model may think for a while before it writes anything. Nothing is streamed during either,
 * so without this the bubble is simply empty — which reads, from the sofa, as nothing having
 * happened at all.
 *
 * These are the icon set's `streaming` glyph made to move: three dots, and that glyph means one
 * thing only — an agent writing an answer into a chat. It is deliberately not the bar the rest of
 * the app uses for a hub round-trip, because this is not a round-trip; it is an answer beginning.
 *
 * The lift, its stagger and its cycle are the same tokens the PIN pad's dots wave on, so the app
 * keeps one idiom for dots that carry a wait.
 */
@Composable
fun ThinkingDots(modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .testTag(THINKING_DOTS_TAG)
                // One node, read once: the word is what a screen reader says, the dots are silent.
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(DOTS) { index ->
                Box(
                    modifier =
                        Modifier
                            .offset(y = lift(index))
                            .size(HearthTheme.size.thinkingDot)
                            .background(HearthTheme.colors.textMuted, CircleShape)
                            .clearAndSetSemantics {},
                )
            }
        }
        // Said in words as well as drawn: dots alone were easy to read as a glitch.
        Text(
            text = stringResource(Res.string.conversation_thinking),
            style = HearthTheme.typography.caption,
            color = HearthTheme.colors.textMuted,
        )
    }
}

/**
 * One [HearthMotion.waveLift] step travelling left to right, then a rest before it repeats.
 *
 * With motion off this is a flat zero — the resting frame, three dots sitting still. It has to be
 * a constant rather than a stopped animation: an infinite transition never lets a composition go
 * idle, and a UI test mounting this screen would time out rather than fail with something
 * readable (design notes §6.21).
 */
@Composable
private fun lift(position: Int): Dp {
    val motion = HearthTheme.motion
    if (!motion.animate) return HearthTheme.spacing.none

    val transition = rememberInfiniteTransition(label = "thinking")
    val step by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = motion.wave.inWholeMilliseconds.toInt()),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset((motion.waveStagger * position).inWholeMilliseconds.toInt()),
            ),
        label = "thinkingLift",
    )
    return motion.waveLift * -step
}

private const val DOTS = 3
