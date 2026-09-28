package com.homelab.household.app.screens.conversation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import com.homelab.household.app.components.ToolRecordLine
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.tool_automatic_undo
import com.homelab.household.app.theme.HearthShapes
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.presentation.chatsession.AutomaticWrite
import org.jetbrains.compose.resources.stringResource

/**
 * "Adding events is now automatic · Undo", after the answer whose card was ticked (canvas:
 * ToolAutoApproved). A quiet line like a step's, with the one way back beside it.
 */
@Composable
fun NowAutomaticLine(
    made: AutomaticWrite,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val words = toolLabel(made.tool, made.action).nowAutomatic ?: return
    ToolRecordLine(
        icon = HearthIcon.ToolGeneric,
        text = AnnotatedString(stringResource(words)),
        modifier = modifier,
        trailing = {
            // The line itself is short; the button keeps a full finger's height around its word.
            Box(
                modifier =
                    Modifier
                        .heightIn(min = HearthTheme.size.touchTarget)
                        .clip(HearthShapes.item)
                        .clickable(role = Role.Button, onClick = onUndo)
                        .padding(horizontal = HearthTheme.spacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.tool_automatic_undo),
                    style = HearthTheme.typography.labelStrong,
                    color = HearthTheme.colors.primary,
                )
            }
        },
    )
}
