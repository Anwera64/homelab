package com.homelab.household.app.screens.pinentry

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.homelab.household.app.components.MemberAvatar
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.icons.HearthIconImage
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.pin_attempts_left
import com.homelab.household.app.resources.pin_back
import com.homelab.household.app.resources.pin_delete
import com.homelab.household.app.resources.pin_entered
import com.homelab.household.app.resources.pin_failed
import com.homelab.household.app.resources.pin_locked
import com.homelab.household.app.resources.pin_locked_countdown
import com.homelab.household.app.resources.pin_title
import com.homelab.household.app.resources.pin_unreachable
import com.homelab.household.app.resources.pin_wrong
import com.homelab.household.app.theme.DayNightPreviews
import com.homelab.household.app.theme.HearthShapes
import com.homelab.household.app.theme.HearthTheme
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
    modifier: Modifier = Modifier
) {
    val colors = HearthTheme.colors
    val spacing = HearthTheme.spacing
    val backDescription = stringResource(Res.string.pin_back)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
            .safeDrawingPadding()
    ) {
        Box(modifier = Modifier.padding(start = spacing.lg, top = spacing.xxl)) {
            Box(
                modifier = Modifier
                    .size(HearthTheme.size.touchTarget)
                    .clip(CircleShape)
                    .clickable(onClick = onBack)
                    .semantics { contentDescription = backDescription },
                contentAlignment = Alignment.Center
            ) {
                HearthIconImage(icon = HearthIcon.Back, contentDescription = null, tint = colors.textMuted)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = spacing.xxl, end = spacing.xxl, top = spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.lg)
        ) {
            MemberAvatar(
                name = state.member.name,
                colour = state.member.avatarColor,
                size = HearthTheme.size.tile,
                glyph = HearthTheme.typography.glyphXxl
            )
            Text(
                text = stringResource(Res.string.pin_title, state.member.name),
                style = HearthTheme.typography.title,
                color = colors.textPrimary
            )
        }

        Dots(
            entered = state.entered,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = spacing.xxl, bottom = spacing.sm)
        )

        StatusLine(
            status = state.status,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.xxl)
        )

        Keypad(
            onDigit = onDigit,
            onDelete = onDelete,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = spacing.xxxl, end = spacing.xxxl, top = spacing.xl, bottom = spacing.xxxl)
        )
    }
}

@Composable
private fun Dots(entered: Int, modifier: Modifier) {
    val colors = HearthTheme.colors
    val description = pluralStringResource(Res.plurals.pin_entered, entered, entered)

    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.lg, Alignment.CenterHorizontally)
    ) {
        repeat(Pin.LENGTH) { index ->
            val dot = Modifier.size(HearthTheme.size.pinDot)
            Box(
                if (index < entered) {
                    dot.background(colors.primary, CircleShape)
                } else {
                    dot.border(HearthTheme.size.hairline, colors.outline, CircleShape)
                }
            )
        }
    }
}

@Composable
private fun StatusLine(status: PinStatus, modifier: Modifier) {
    val colors = HearthTheme.colors
    val caption = HearthTheme.typography.caption

    @Composable
    fun line(text: String, warning: Boolean) =
        Text(text = text, style = caption, color = if (warning) colors.error else colors.textMuted, textAlign = TextAlign.Center)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xs)
    ) {
        when (status) {
            PinStatus.Idle, PinStatus.Checking -> Unit
            // The canvas draws the warning from two tries out; before that a miss is just a miss.
            is PinStatus.WrongPin -> if (status.attemptsLeft <= WARN_FROM_ATTEMPTS_LEFT) {
                line(pluralStringResource(Res.plurals.pin_attempts_left, status.attemptsLeft, status.attemptsLeft), warning = false)
            } else {
                line(stringResource(Res.string.pin_wrong), warning = true)
            }

            is PinStatus.Locked -> {
                line(stringResource(Res.string.pin_locked), warning = true)
                line(pluralStringResource(Res.plurals.pin_locked_countdown, status.secondsLeft, status.secondsLeft), warning = true)
            }

            PinStatus.Unreachable -> line(stringResource(Res.string.pin_unreachable), warning = true)
            PinStatus.Failed -> line(stringResource(Res.string.pin_failed), warning = true)
        }
    }
}

@Composable
private fun Keypad(onDigit: (Char) -> Unit, onDelete: () -> Unit, modifier: Modifier) {
    val spacing = HearthTheme.spacing
    val deleteDescription = stringResource(Res.string.pin_delete)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.lg, Alignment.CenterVertically)
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
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = HearthTheme.size.tile)
                    .clip(HearthShapes.key)
                    .clickable(onClick = onDelete)
                    .semantics { contentDescription = deleteDescription },
                contentAlignment = Alignment.Center
            ) {
                HearthIconImage(
                    icon = HearthIcon.Delete,
                    contentDescription = null,
                    size = HearthTheme.size.iconXl,
                    tint = HearthTheme.colors.textMuted
                )
            }
        }
    }
}

@Composable
private fun DigitKey(digit: Char, onDigit: (Char) -> Unit, modifier: Modifier) {
    val colors = HearthTheme.colors
    Box(
        modifier = modifier
            .heightIn(min = HearthTheme.size.tile)
            .clip(HearthShapes.key)
            .background(colors.surface)
            .clickable { onDigit(digit) },
        contentAlignment = Alignment.Center
    ) {
        Text(text = digit.toString(), style = HearthTheme.typography.glyphXxl, color = colors.textPrimary)
    }
}

private const val WARN_FROM_ATTEMPTS_LEFT = 2

@DayNightPreviews
@Composable
private fun PinEntryContentPreview(
    @PreviewParameter(PinEntryUiStateProvider::class) state: PinEntryUiState
) {
    HearthTheme {
        PinEntryContent(state = state, onDigit = {}, onDelete = {}, onBack = {})
    }
}
