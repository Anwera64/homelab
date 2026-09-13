package com.homelab.household.app.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.homelab.household.app.theme.DayNightPreviews
import com.homelab.household.app.theme.HearthTheme

@DayNightPreviews
@Composable
private fun BentoCardLabelledPreview() {
    ComponentPreview {
        BentoCard(label = "Morning briefing", modifier = Modifier.fillMaxWidth()) {
            Text("Quiet morning at home.", style = HearthTheme.typography.bodyLarge)
            Text("Checked your calendar", style = HearthTheme.typography.caption, color = HearthTheme.colors.textMuted)
        }
    }
}

@DayNightPreviews
@Composable
private fun BentoCardUnlabelledPreview() {
    ComponentPreview {
        BentoCard(modifier = Modifier.fillMaxWidth()) {
            Text("Bins go out tonight", style = HearthTheme.typography.bodyStrong)
            Text("Emma is on it this week", style = HearthTheme.typography.body, color = HearthTheme.colors.textMuted)
        }
    }
}
