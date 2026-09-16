package com.homelab.household.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.homelab.household.app.theme.HearthShapes
import com.homelab.household.app.theme.HearthTheme

/**
 * What a destructive action takes and what it leaves, in two boxes. Friction matches damage, and
 * whoever is about to act reads both lists before the button means anything (design notes §2).
 */
@Composable
fun ConsequenceCards(
    erasedTitle: String,
    erased: List<String>,
    staysTitle: String,
    stays: List<String>,
    modifier: Modifier = Modifier
) {
    val colors = HearthTheme.colors

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md)) {
        ConsequenceCard(
            title = erasedTitle,
            lines = erased,
            background = colors.errorContainer,
            ink = colors.onErrorContainer
        )
        ConsequenceCard(
            title = staysTitle,
            lines = stays,
            background = colors.surface,
            ink = colors.textPrimary
        )
    }
}

@Composable
private fun ConsequenceCard(title: String, lines: List<String>, background: Color, ink: Color) {
    val type = HearthTheme.typography

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, HearthShapes.bento)
            .padding(HearthTheme.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm)
    ) {
        Text(title, style = type.overline, color = ink)
        lines.forEach { line ->
            Text(line, style = type.label, color = ink)
        }
    }
}
