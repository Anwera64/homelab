package com.homelab.household.app.components

import androidx.compose.runtime.Composable
import com.homelab.household.app.theme.PreviewDayNight

/** The three owners side by side, the chosen one ticked — the picker sheet's list. */
@PreviewDayNight
@Composable
private fun AgentCardPreview() {
    ComponentPreview {
        AgentCard(
            name = "Home Coordinator",
            avatar = "🏡",
            tagline = "Schedules, meals, keeping the week straight.",
            owner = AgentOwnerChip.BuiltIn("built in"),
            permissions = listOf("Read calendar", "Search the web"),
            selected = true,
            onClick = {},
        )
        AgentCard(
            name = "Hardware Scout",
            avatar = "🔧",
            tagline = "Parts, prices, whether that GPU is worth it.",
            owner = AgentOwnerChip.Yours("yours"),
            permissions = listOf("Search the web"),
            selected = false,
            onClick = {},
        )
        AgentCard(
            name = "Garden Planner",
            avatar = "🌱",
            tagline = "What to sow this month.",
            owner = AgentOwnerChip.Member("Liam’s"),
            permissions = emptyList(),
            selected = false,
            onClick = {},
        )
    }
}
