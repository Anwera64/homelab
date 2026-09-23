package com.homelab.household.app.screens.conversation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.icons.HearthIconImage
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.conversation_change_agent
import com.homelab.household.app.theme.HearthTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * The agent's face on a new chat's greeting — and, until the first message, the way to change it.
 *
 * With [onChangeAgent] it carries the ⇄ badge on its edge and no line of words; avatar and badge
 * together are the tap target, named "Change agent" for a screen reader. Testing showed a picture
 * does not look tappable, so on arrival two rings spread out of the badge once and then it rests.
 * Never a loop: in this system motion means waiting, and an endless pulse would read as loading.
 * Without motion (StillMotion, reduce motion) the resting frame is the badge alone.
 *
 * Without [onChangeAgent] it is only the avatar: a chat that has started keeps its agent.
 */
@Composable
fun AgentAvatarButton(
    avatar: String,
    onChangeAgent: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    val size = HearthTheme.size
    val spacing = HearthTheme.spacing
    val motion = HearthTheme.motion
    val description = stringResource(Res.string.conversation_change_agent)

    val tappable =
        if (onChangeAgent != null) {
            // Not clipped: the badge and its rings reach past the avatar's circle.
            Modifier
                .clickable(onClick = onChangeAgent)
                .semantics {
                    contentDescription = description
                    role = Role.Button
                }
                // Room for the badge, which sits over the edge, inside what can be tapped.
                .padding(spacing.xs)
        } else {
            Modifier
        }

    Box(modifier = modifier.then(tappable)) {
        Box(
            modifier =
                Modifier
                    .size(size.tile)
                    .clip(CircleShape)
                    .background(colors.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = avatar, style = HearthTheme.typography.glyphXxl)
        }
        if (onChangeAgent == null) return@Box

        if (motion.animate) {
            val rings = remember { List(2) { Animatable(0f) } }
            LaunchedEffect(Unit) {
                rings.forEachIndexed { index, ring ->
                    launch {
                        delay(motion.nudgeStagger * index)
                        ring.animateTo(
                            targetValue = 1f,
                            animationSpec =
                                tween(
                                    durationMillis = motion.nudge.inWholeMilliseconds.toInt(),
                                    easing = NudgeEasing,
                                ),
                        )
                    }
                }
            }
            rings.forEach { ring ->
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .offset(spacing.xs, spacing.xs)
                            .size(size.iconXl)
                            .graphicsLayer {
                                val progress = ring.value
                                val scale = 1f + (NUDGE_SPREAD - 1f) * progress
                                scaleX = scale
                                scaleY = scale
                                // Nothing before the ring starts, and nothing once it has spread.
                                alpha = if (progress <= 0f || progress >= 1f) 0f else NUDGE_ALPHA * (1f - progress)
                            }.border(size.emphasis, colors.primary, CircleShape),
                )
            }
        }

        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(spacing.xs, spacing.xs)
                    .size(size.iconXl)
                    .clip(CircleShape)
                    .background(colors.surface)
                    .border(size.hairline, colors.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            HearthIconImage(
                icon = HearthIcon.AgentSwap,
                contentDescription = null,
                size = size.iconSm,
                tint = colors.primary,
            )
        }
    }
}

/** How far a ring spreads, as a multiple of the badge. */
private const val NUDGE_SPREAD = 2.1f

/** How strong a ring is as it leaves the badge. */
private const val NUDGE_ALPHA = 0.55f

/** Quick out of the badge, slow to fade: the canvas's curve. */
private val NudgeEasing = CubicBezierEasing(0.2f, 0.6f, 0.3f, 1f)
