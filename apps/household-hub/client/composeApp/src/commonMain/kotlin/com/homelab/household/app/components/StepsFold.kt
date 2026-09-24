package com.homelab.household.app.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.AnnotatedString
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.icons.HearthIconImage
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.conversation_steps_hide
import com.homelab.household.app.resources.conversation_steps_show
import com.homelab.household.app.theme.HearthShapes
import com.homelab.household.app.theme.HearthTheme
import org.jetbrains.compose.resources.pluralStringResource

/**
 * A run of steps a finished answer took between two stretches of its words, folded into one quiet
 * line where it happened. The [label] says what the steps did ("Searched the web, read 2 pages ⌄",
 * #40); a screen reader is also told how many there are. Tapping it shows the steps in place;
 * tapping again hides them.
 *
 * Closed to begin with, so a finished answer reads as the answer; the steps are one tap away
 * rather than gone. Whether it is open lasts as long as the conversation is on screen.
 */
@Composable
fun StepsFold(
    label: AnnotatedString,
    count: Int,
    modifier: Modifier = Modifier,
    steps: @Composable () -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val tint = HearthTheme.colors.textMuted
    val action =
        pluralStringResource(
            if (open) Res.plurals.conversation_steps_hide else Res.plurals.conversation_steps_show,
            count,
            count,
        )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xs)) {
        Row(
            modifier =
                Modifier
                    .minimumInteractiveComponentSize()
                    .clip(HearthShapes.item)
                    .clickable(onClickLabel = action) { open = !open }
                    .padding(horizontal = HearthTheme.spacing.xs, vertical = HearthTheme.spacing.xxs),
            horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = HearthTheme.typography.label,
                color = tint,
            )
            // The set's one chevron, turned to point where the steps will go.
            HearthIconImage(
                icon = HearthIcon.ChevronRight,
                contentDescription = null,
                size = HearthTheme.size.iconSm,
                tint = tint,
                modifier = Modifier.rotate(if (open) -90f else 90f),
            )
        }
        if (open) steps()
    }
}
