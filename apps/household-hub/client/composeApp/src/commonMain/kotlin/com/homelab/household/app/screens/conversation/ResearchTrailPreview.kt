package com.homelab.household.app.screens.conversation

import androidx.compose.runtime.Composable
import com.homelab.household.app.components.ComponentPreview
import com.homelab.household.app.components.ToolRecordLine
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.app.theme.PreviewDayNight
import org.jetbrains.compose.resources.stringResource

/**
 * The lines a research turn leaves, read from the real labels: a search, a page read, one that
 * couldn't be, and a look through what was read. Canvas: Tools & web search · Research.
 */
@PreviewDayNight
@Composable
private fun ResearchTrailPreview() {
    ComponentPreview {
        listOf("searxng_search", "read_page").forEach { tool ->
            val label = toolLabel(tool)
            ToolRecordLine(icon = label.icon, text = stringResource(label.done))
        }
        ToolRecordLine(
            icon = HearthIcon.Error,
            text = stringResource(toolLabel("read_page").failed),
            tint = HearthTheme.colors.error,
        )
        toolLabel("lookup_sources").let { label ->
            ToolRecordLine(icon = label.icon, text = stringResource(label.done))
        }
    }
}
