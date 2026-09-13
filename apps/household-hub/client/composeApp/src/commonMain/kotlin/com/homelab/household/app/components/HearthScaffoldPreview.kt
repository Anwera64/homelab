package com.homelab.household.app.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.homelab.household.app.theme.DayNightPreviews
import com.homelab.household.app.theme.HearthTheme

/** Header and nav stay put while more cards than fit pass between them. */
@DayNightPreviews
@Composable
private fun HearthScaffoldPreview() {
    HearthTheme {
        HearthScaffold(
            modifier = Modifier.fillMaxSize(),
            header = { HearthTopBar(onBack = {}, backDescription = "Back", title = "Household") },
            bottomBar = { HearthBottomNav(selected = NavTab.Household, onSelect = {}, onNewChat = {}) }
        ) { padding ->
            LazyColumn(
                contentPadding = padding,
                verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md)
            ) {
                items(sampleCards) { (label, text) ->
                    BentoCard(modifier = Modifier.fillMaxWidth(), label = label) {
                        Text(text = text, style = HearthTheme.typography.body)
                    }
                }
            }
        }
    }
}

private val sampleCards = listOf(
    "Morning briefing" to "Quiet morning at home. Bins go out tonight.",
    "Today" to "08:00 · School run · Emma",
    "Groceries" to "Oat milk, eggs, basil, dishwasher tablets",
    "Chores" to "Water the plants · Hoover the stairs",
    "Bills" to "Electricity due Friday · 64,20 €",
    "Dinner" to "Lentil soup, then the leftover tart",
    "Garden" to "Tomatoes need tying up before the weekend",
    "Hub" to "Online · last synced a minute ago"
)
