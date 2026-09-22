package com.homelab.household.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.homelab.household.app.theme.HearthShapes
import com.homelab.household.app.theme.HearthTheme

/**
 * One turn in a conversation.
 *
 * A question is a bubble on the right; an answer is prose on the left with no chrome around it,
 * because it is the thing you came to read and a card would only put a frame on it.
 */
@Composable
fun MessageBubble(
    content: String,
    fromMe: Boolean,
    modifier: Modifier = Modifier,
    status: (@Composable () -> Unit)? = null,
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (fromMe) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm),
    ) {
        if (fromMe) {
            Box(
                modifier =
                    Modifier
                        .background(colors.primary, HearthShapes.item)
                        .padding(
                            horizontal = HearthTheme.spacing.lg,
                            vertical = HearthTheme.spacing.md,
                        ),
            ) {
                Text(text = content, style = type.body, color = colors.onPrimary)
            }
        } else {
            Text(text = content, style = type.bodyLarge, color = colors.textPrimary)
        }

        status?.invoke()
    }
}

/**
 * The line that sits where an answer stopped.
 *
 * Inline rather than in a row of its own, and at the end of the text rather than the foot of the
 * screen: that is where the eye already is when the words stop arriving.
 */
@Composable
fun TurnStatusLine(
    label: String,
    detail: String?,
    modifier: Modifier = Modifier,
    tone: TurnStatusTone = TurnStatusTone.Working,
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography
    val labelColour =
        when (tone) {
            TurnStatusTone.Working -> colors.textMuted
            TurnStatusTone.Wrong -> colors.error
        }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = type.labelStrong, color = labelColour)
        if (detail != null) {
            Text(text = detail, style = type.caption, color = colors.textMuted)
        }
    }
}

enum class TurnStatusTone {
    Working,
    Wrong,
}
