package com.homelab.household.app.components

import androidx.compose.runtime.Composable
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.theme.DayNightPreviews

/** The line a tool leaves above an answer. */
@DayNightPreviews
@Composable
private fun ToolRecordLinePreview() {
    ComponentPreview {
        ToolRecordLine(icon = HearthIcon.CalendarAdd, text = "Checked your calendar")
    }
}
