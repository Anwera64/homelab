package com.homelab.household.app.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle

/**
 * The type scale, rendered — the code's side of the proof artboard on the design canvas. Keep it
 * next to the tokens: a role that looks wrong next to its neighbours is easier to see than to
 * argue about.
 */
@PreviewDayNight
@Composable
private fun TypeScalePreview() {
    HearthTheme {
        val type = HearthTheme.typography
        val colors = HearthTheme.colors
        Column(
            modifier =
                Modifier
                    .background(colors.canvas)
                    .verticalScroll(rememberScrollState())
                    .padding(HearthTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md),
        ) {
            Specimen("hero", "Evening, Emma", type.hero)
            Specimen("title", "Unlock secret chats", type.title)
            Specimen("heading", "Morning briefing", type.heading)
            Specimen("bodyLarge", "Quiet morning at home.", type.bodyLarge)
            Specimen("body", "The default, if you are unsure.", type.body)
            Specimen("bodyStrong", "Jury prep · panel review", type.bodyStrong)
            Specimen("label", "UPC Barcelona", type.label)
            Specimen("labelStrong", "Household admin", type.labelStrong)
            Specimen("caption", "6 digits · only you will know it", type.caption)
            Specimen("overline", "MORNING BRIEFING", type.overline)
            Specimen("micro", "Household", type.micro)
            Specimen("monoSm", "Hub online · 12 ms", type.monoSm)
            Specimen("mono", "08:00 · garden-planner", type.mono)
            Specimen("monoLg", "abcd-efgh-ijkl-mnop", type.monoLg)
            Specimen("codeHero", "K7M2QP", type.codeHero)
            Specimen("glyphMd", "E", type.glyphMd)
            Specimen("glyphXxl", "E", type.glyphXxl)
        }
    }
}

@Composable
private fun Specimen(
    name: String,
    sample: String,
    style: TextStyle,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.lg),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = name,
            style = HearthTheme.typography.monoSm,
            color = HearthTheme.colors.textMuted,
            modifier = Modifier.padding(bottom = HearthTheme.spacing.xxs),
        )
        Text(text = sample, style = style, color = HearthTheme.colors.textPrimary)
    }
}
