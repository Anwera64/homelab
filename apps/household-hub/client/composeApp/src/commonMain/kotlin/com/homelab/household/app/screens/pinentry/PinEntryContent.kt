package com.homelab.household.app.screens.pinentry

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import com.homelab.household.app.components.HearthScaffold
import com.homelab.household.app.components.HearthTopBar
import com.homelab.household.app.components.MemberAvatar
import com.homelab.household.app.components.SlowLine
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.icons.HearthIconImage
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.a11y_pin_checking
import com.homelab.household.app.resources.pin_attempts_left
import com.homelab.household.app.resources.pin_back
import com.homelab.household.app.resources.pin_delete
import com.homelab.household.app.resources.pin_entered
import com.homelab.household.app.resources.pin_failed
import com.homelab.household.app.resources.pin_forgotten
import com.homelab.household.app.resources.pin_locked
import com.homelab.household.app.resources.pin_locked_countdown
import com.homelab.household.app.resources.pin_title
import com.homelab.household.app.resources.pin_unreachable
import com.homelab.household.app.resources.pin_wrong
import com.homelab.household.app.theme.DayNightPreviews
import com.homelab.household.app.theme.HearthShapes
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.app.util.WaitPhase
import com.homelab.household.app.util.rememberWaitPhase
import com.homelab.household.domain.model.Pin
import com.homelab.household.presentation.pinentry.PinEntryUiState
import com.homelab.household.presentation.pinentry.PinStatus
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The PIN pad: a face, six dots, a line saying where things stand, and the keys. Fixed shape, so
 * it doesn't scroll — the keys take whatever height is left.
 *
 * Stateless — [PinEntryScreen] owns the ViewModel.
 */
@Composable
fun PinEntryContent(
    state: PinEntryUiState,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    onForgotten: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HearthScaffold(
        modifier = modifier,
        header = { HearthTopBar(onBack = onBack, backDescription = stringResource(Res.string.pin_back)) },
        gutter = HearthTheme.spacing.none,
    ) { padding ->
        PinPad(
            state = state,
            onDigit = onDigit,
            onDelete = onDelete,
            onForgotten = onForgotten,
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }
}

@Composable
private fun PinPad(
    state: PinEntryUiState,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onForgotten: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    val spacing = HearthTheme.spacing

    Column(modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = spacing.xxl, end = spacing.xxl, top = spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            MemberAvatar(
                name = state.member.name,
                colour = state.member.avatarColor,
                size = HearthTheme.size.tile,
                glyph = HearthTheme.typography.glyphXxl,
            )
            Text(
                text = stringResource(Res.string.pin_title, state.member.name),
                style = HearthTheme.typography.title,
                color = colors.textPrimary,
            )
        }

        // The wave obeys the same four beats as every other wait, so a hub answering in 40ms on
        // the LAN never makes the dots twitch.
        val wait = rememberWaitPhase(state.status == PinStatus.Checking)

        Dots(
            entered = state.entered,
            waving = wait != WaitPhase.Hidden,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = spacing.xxl, bottom = spacing.sm),
        )

        StatusLine(
            status = state.status,
            wait = wait,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.xxl),
        )

        // Recovery is social, and it starts here: there is no email to send a reset to.
        Text(
            text = stringResource(Res.string.pin_forgotten),
            style = HearthTheme.typography.bodyStrong,
            color = colors.primary,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable(onClick = onForgotten)
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
        )

        Keypad(
            onDigit = onDigit,
            onDelete = onDelete,
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = spacing.xxxl, end = spacing.xxxl, top = spacing.xl, bottom = spacing.xxxl),
        )
    }
}

const val PinDotsTag = "PinDots"

/**
 * The six dots, and while the hub has the PIN, the wave (design notes §6.21, pattern 5).
 *
 * The pad submits on the sixth digit, so there is no button for a bar to run along; the dots are
 * the only thing on screen that belongs to the call. One [HearthMotion.waveLift] step of lift
 * travels left to right — toward the hub — then rests before it repeats. No dot dims, changes
 * colour or resizes: motion alone carries it, so nothing readable is greyed out (§2) and the keys
 * keep full contrast while the pad ignores them.
 *
 * The row already collapses to one description rather than six, so the wave adds a state beside
 * it rather than a seventh thing to read out.
 */
@Composable
private fun Dots(
    entered: Int,
    waving: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    val description = pluralStringResource(Res.plurals.pin_entered, entered, entered)
    val checking = stringResource(Res.string.a11y_pin_checking)

    Row(
        modifier =
            modifier
                .testTag(PinDotsTag)
                .clearAndSetSemantics {
                    contentDescription = description
                    if (waving) {
                        stateDescription = checking
                        liveRegion = LiveRegionMode.Polite
                    }
                },
        horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.lg, Alignment.CenterHorizontally),
    ) {
        repeat(Pin.LENGTH) { index ->
            val filled = index < entered
            val dot =
                Modifier
                    .size(HearthTheme.size.pinDot)
                    .offset(y = if (filled) waveLift(waving, index) else HearthTheme.spacing.none)
            Box(
                if (filled) {
                    dot.background(colors.primary, CircleShape)
                } else {
                    dot.border(HearthTheme.size.hairline, colors.outline, CircleShape)
                },
            )
        }
    }
}

/**
 * How far one dot is lifted right now. [position] puts it a beat behind its neighbour, which is
 * what turns six dots bobbing into one wave crossing them.
 *
 * Not waving, or motion off, is a flat zero rather than a stopped animation: an infinite
 * transition keeps the composition from ever going idle, and a UI test under one hangs instead of
 * failing (see `HearthMotion`).
 */
@Composable
private fun waveLift(
    waving: Boolean,
    position: Int,
): Dp {
    val motion = HearthTheme.motion
    if (!waving || !motion.animate) return HearthTheme.spacing.none

    val transition = rememberInfiniteTransition(label = "pinWave")
    val lift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = motion.wave.inWholeMilliseconds.toInt()),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset((motion.waveStagger * position).inWholeMilliseconds.toInt()),
            ),
        label = "pinWaveLift",
    )
    return motion.waveLift * -lift
}

/**
 * Where the pad stands, in words. It keeps one caption line of height whether or not it has any:
 * this is a fixed-shape screen, and a line appearing — the slow one at 8 seconds, or a miss —
 * would otherwise shove the whole keypad down under the reader's thumb.
 */
@Composable
private fun StatusLine(
    status: PinStatus,
    wait: WaitPhase,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    val caption = HearthTheme.typography.caption

    @Composable
    fun line(
        text: String,
        warning: Boolean,
    ) = Text(
        text = text,
        style = caption,
        color = if (warning) colors.error else colors.textMuted,
        textAlign = TextAlign.Center,
    )

    Column(
        modifier = modifier.heightIn(min = HearthTheme.size.statusLine),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xs),
    ) {
        when (status) {
            // Checking says nothing here: the dots are already saying it, and the slow line is
            // what turns up in this reserved space if the hub takes its time.
            PinStatus.Idle, PinStatus.Checking -> {
                SlowLine(wait)
            }

            // The canvas draws the warning from two tries out; before that a miss is just a miss.
            is PinStatus.WrongPin -> {
                if (status.attemptsLeft <= WARN_FROM_ATTEMPTS_LEFT) {
                    line(
                        pluralStringResource(Res.plurals.pin_attempts_left, status.attemptsLeft, status.attemptsLeft),
                        warning = false,
                    )
                } else {
                    line(stringResource(Res.string.pin_wrong), warning = true)
                }
            }

            is PinStatus.Locked -> {
                line(stringResource(Res.string.pin_locked), warning = true)
                line(
                    pluralStringResource(Res.plurals.pin_locked_countdown, status.secondsLeft, status.secondsLeft),
                    warning = true,
                )
            }

            PinStatus.Unreachable -> {
                line(stringResource(Res.string.pin_unreachable), warning = true)
            }

            PinStatus.Failed -> {
                line(stringResource(Res.string.pin_failed), warning = true)
            }
        }
    }
}

@Composable
private fun Keypad(
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = HearthTheme.spacing
    val deleteDescription = stringResource(Res.string.pin_delete)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.lg, Alignment.CenterVertically),
    ) {
        listOf("123", "456", "789").forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.lg)) {
                row.forEach { digit -> DigitKey(digit = digit, onDigit = onDigit, modifier = Modifier.weight(1f)) }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.lg)) {
            Spacer(Modifier.weight(1f))
            DigitKey(digit = '0', onDigit = onDigit, modifier = Modifier.weight(1f))
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = HearthTheme.size.tile)
                        .clip(HearthShapes.key)
                        .clickable(onClick = onDelete)
                        .semantics { contentDescription = deleteDescription },
                contentAlignment = Alignment.Center,
            ) {
                HearthIconImage(
                    icon = HearthIcon.Delete,
                    contentDescription = null,
                    size = HearthTheme.size.iconXl,
                    tint = HearthTheme.colors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun DigitKey(
    digit: Char,
    onDigit: (Char) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    Box(
        modifier =
            modifier
                .heightIn(min = HearthTheme.size.tile)
                .clip(HearthShapes.key)
                .background(colors.surface)
                .clickable { onDigit(digit) },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = digit.toString(), style = HearthTheme.typography.glyphXxl, color = colors.textPrimary)
    }
}

private const val WARN_FROM_ATTEMPTS_LEFT = 2

@DayNightPreviews
@Composable
private fun PinEntryContentPreview(
    @PreviewParameter(PinEntryUiStateProvider::class) state: PinEntryUiState,
) {
    HearthTheme {
        PinEntryContent(state = state, onDigit = {}, onDelete = {}, onBack = {}, onForgotten = {})
    }
}
